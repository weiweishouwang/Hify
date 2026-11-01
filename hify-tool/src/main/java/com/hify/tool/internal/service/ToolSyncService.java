package com.hify.tool.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.api.ToolSyncResp;
import com.hify.tool.api.ToolProvider;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import com.hify.shared.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 目录同步：把各来源「代码里注册的工具」同步进 {@code tool_definition}。
 *
 * <p><b>同步的不是数据，是代码的镜像。</b>内置工具随应用发布、MCP 工具随上游变化，
 * 目录表里对应的那几行由本服务托管：新建、更新、消失下架。用户只手工创建 http 来源。
 *
 * <p><b>两阶段</b>：先向各 provider 要工具列表（这一步可能发起网络调用，MCP 就是），
 * 再在事务里落库 —— 事务方法内禁止调用慢的外部能力（CLAUDE.md §3.1 E5）。
 *
 * <p><b>不物理删除任何行</b>：目录行可能被 Agent 引用着，删了就出悬挂 id。
 * 工具消失时把行置 {@code enabled = 0}。
 */
@Service
public class ToolSyncService {

    private static final Logger log = LoggerFactory.getLogger(ToolSyncService.class);

    /** 初始配置版本。 */
    private static final long INITIAL_VERSION = 1L;

    private final List<ToolProvider> providers;
    private final ToolDefinitionMapper tools;
    private final TransactionTemplate transactionTemplate;

    public ToolSyncService(List<ToolProvider> providers, ToolDefinitionMapper tools,
                           PlatformTransactionManager transactionManager) {
        this.providers = providers;
        this.tools = tools;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 从各来源重新发现工具并同步进目录。
     *
     * @return 同步统计
     */
    public ToolSyncResp syncAll() {
        // 第一阶段：向各来源要工具列表，不持有事务
        List<SourcePlan> plans = new ArrayList<>();
        for (ToolProvider provider : providers) {
            String sourceType = provider.sourceType();
            List<ToolSpec> specs;
            try {
                specs = provider.listTools();
            } catch (RuntimeException exception) {
                log.warn("工具来源发现失败 sourceType={} reason={}", sourceType, exception.getClass().getSimpleName());
                continue;
            }
            if (specs == null || specs.isEmpty()) {
                if (!provider.managesDirectoryRows()) {
                    // 空列表表示该来源不由 provider 发现（http 就是这样）——
                    // 这种情况不做任何下架处理，否则会把整批工具误停。
                    log.info("工具来源本次没有可发现的工具，跳过 sourceType={}", sourceType);
                    continue;
                }
                // 本来源托管目录行：空列表 = 工具全部下架
                // （内置工具被从代码里删掉、MCP 的最后一个服务被删掉）。
                // 仍然走一遍计划，applyPlans 会把该来源的行全部置停用。
                log.info("工具来源已无工具，全部下架 sourceType={}", sourceType);
                plans.add(new SourcePlan(sourceType, List.of()));
                continue;
            }
            plans.add(new SourcePlan(sourceType, specs));
        }

        // 第二阶段：事务里落库
        return transactionTemplate.execute(status -> applyPlans(plans));
    }

    private ToolSyncResp applyPlans(List<SourcePlan> plans) {
        List<ToolSyncResp.SourceStat> stats = new ArrayList<>();
        int totalDiscovered = 0;
        int totalCreated = 0;
        int totalUpdated = 0;
        int totalDisabled = 0;

        for (SourcePlan plan : plans) {
            Set<String> seen = new HashSet<>();
            int created = 0;
            int updated = 0;
            int skipped = 0;

            for (ToolSpec spec : plan.specs()) {
                if (spec == null || spec.toolKey() == null || spec.toolKey().isBlank()) {
                    skipped++;
                    continue;
                }
                seen.add(spec.toolKey());
                Outcome outcome = applySpec(plan.sourceType(), spec);
                if (outcome == Outcome.CREATED) {
                    created++;
                } else if (outcome == Outcome.UPDATED) {
                    updated++;
                } else if (outcome == Outcome.SKIPPED) {
                    skipped++;
                }
            }

            int disabled = disableVanished(plan.sourceType(), seen);
            if (skipped > 0) {
                log.warn("工具来源有工具未同步 sourceType={} skipped={}", plan.sourceType(), skipped);
            }
            stats.add(new ToolSyncResp.SourceStat(plan.sourceType(), plan.specs().size(), created, updated, disabled));
            totalDiscovered += plan.specs().size();
            totalCreated += created;
            totalUpdated += updated;
            totalDisabled += disabled;
        }

        ToolSyncResp result = new ToolSyncResp();
        result.setSources(stats);
        result.setDiscovered(totalDiscovered);
        result.setCreated(totalCreated);
        result.setUpdated(totalUpdated);
        result.setDisabled(totalDisabled);
        return result;
    }

    /** 新建或更新一个工具的目录行。 */
    private Outcome applySpec(String sourceType, ToolSpec spec) {
        Long existingId = tools.findIdByToolKey(sourceType, spec.toolKey());
        Long nameOwner = spec.name() == null ? null : tools.findIdByName(spec.name());
        if (nameOwner != null && !nameOwner.equals(existingId)) {
            // 名字被别的工具占着：不写进去，避免表里出现两个同名工具让 function calling 撞名
            log.warn("工具名已被占用，跳过同步 sourceType={} toolKey={} name={}",
                    sourceType, spec.toolKey(), spec.name());
            return Outcome.SKIPPED;
        }

        if (existingId == null) {
            ToolDefinitionDO entity = new ToolDefinitionDO();
            entity.setPublicId(IdGenerator.nextUlid());
            entity.setSourceType(sourceType);
            entity.setToolKey(spec.toolKey());
            entity.setName(spec.name());
            entity.setDescription(spec.description());
            entity.setSchema(spec.schema());
            entity.setConfig(spec.config());
            entity.setEnabled(true);
            entity.setConfigVersion(INITIAL_VERSION);
            tools.insert(entity);
            return Outcome.CREATED;
        }

        ToolDefinitionDO existing = tools.selectById(existingId);
        if (existing == null) {
            return Outcome.SKIPPED;
        }
        if (unchanged(existing, spec)) {
            return Outcome.UNCHANGED;
        }
        existing.setName(spec.name());
        existing.setDescription(spec.description());
        existing.setSchema(spec.schema());
        existing.setConfig(spec.config());
        existing.setEnabled(true);
        existing.setConfigVersion(existing.getConfigVersion() == null ? INITIAL_VERSION : existing.getConfigVersion() + 1);
        tools.updateById(existing);
        return Outcome.UPDATED;
    }

    /** 本次没被发现的行置为停用；不物理删除。 */
    private int disableVanished(String sourceType, Set<String> seen) {
        int disabled = 0;
        for (String toolKey : tools.listToolKeysBySource(sourceType)) {
            if (seen.contains(toolKey)) {
                continue;
            }
            ToolDefinitionDO row = tools.selectOne(new QueryWrapper<ToolDefinitionDO>()
                    .eq("source_type", sourceType).eq("tool_key", toolKey));
            if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
                continue;
            }
            row.setEnabled(false);
            tools.updateById(row);
            disabled++;
        }
        return disabled;
    }

    /** 目录行是否已经与来源一致；一致就不动，避免每次同步都把配置版本顶上去。 */
    private static boolean unchanged(ToolDefinitionDO existing, ToolSpec spec) {
        return Objects.equals(existing.getName(), spec.name())
                && Objects.equals(existing.getDescription(), spec.description())
                && Objects.equals(existing.getSchema(), spec.schema())
                && Objects.equals(existing.getConfig(), spec.config())
                && Boolean.TRUE.equals(existing.getEnabled());
    }

    /** 供扩展：一次同步的输入：某个来源这次发现的工具。 */
    private record SourcePlan(String sourceType, List<ToolSpec> specs) {
    }

    /** 单个工具的处理结果。 */
    private enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED,
        SKIPPED
    }
}
