package com.hify.workflow.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.json.JsonUtils;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import com.hify.workflow.api.WorkflowCreateReq;
import com.hify.workflow.api.WorkflowDefinition;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowListQuery;
import com.hify.workflow.api.WorkflowListResp;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowRunSummaryResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowSummaryResp;
import com.hify.workflow.api.WorkflowUpdateReq;
import com.hify.workflow.internal.converter.WorkflowConverter;
import com.hify.workflow.internal.entity.WorkflowDefinitionDO;
import com.hify.workflow.internal.entity.WorkflowRunDO;
import com.hify.workflow.internal.mapper.WorkflowMapper;
import com.hify.workflow.internal.mapper.WorkflowRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工作流定义的增删改查与执行。
 *
 * <p><b>写库的每个动作单独一个事务</b> —— 执行过程里有模型调用，不能包进事务
 * （CLAUDE.md §3.1 E5）。
 */
@Service
public class WorkflowServiceImpl implements WorkflowService {

    /** 运行记录列表默认返回条数。 */
    private static final int DEFAULT_RUN_LIMIT = 20;

    /** 运行记录列表上限。 */
    private static final int MAX_RUN_LIMIT = 100;

    private final WorkflowMapper workflows;
    private final WorkflowRunMapper runs;
    private final WorkflowSupport support;
    private final WorkflowEngine engine;
    private final JsonUtils json;
    private final WorkflowConverter converter;

    public WorkflowServiceImpl(WorkflowMapper workflows, WorkflowRunMapper runs, WorkflowSupport support,
                               WorkflowEngine engine, JsonUtils json, WorkflowConverter converter) {
        this.workflows = workflows;
        this.runs = runs;
        this.support = support;
        this.engine = engine;
        this.json = json;
        this.converter = converter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResp create(WorkflowCreateReq request) {
        if (request == null) {
            throw WorkflowSupport.invalid("工作流配置不能为空");
        }
        String name = WorkflowSupport.name(request.getName());
        requireAvailableName(name, null);
        WorkflowDefinition definition = support.parse(request.getDefinition());

        WorkflowDefinitionDO entity = new WorkflowDefinitionDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setName(name);
        entity.setDescription(WorkflowSupport.description(request.getDescription()));
        entity.setDefinition(definition);
        entity.setVersion(WorkflowSupport.INITIAL_VERSION);
        entity.setStatus(WorkflowSupport.status(request.getStatus(), WorkflowSupport.STATUS_DRAFT));
        try {
            workflows.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "工作流名称已被占用（包括已删除记录）", exception);
        }
        return converter.toDetail(entity);
    }

    @Override
    public WorkflowListResp list(WorkflowListQuery query) {
        WorkflowListQuery effective = query == null ? new WorkflowListQuery() : query;
        var wrapper = new QueryWrapper<WorkflowDefinitionDO>();
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus().trim());
        }
        String keyword = effective.getKeyword() == null ? null : effective.getKeyword().trim();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(item -> item.like("name", keyword).or().like("description", keyword));
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<WorkflowDefinitionDO> rows = workflows.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<WorkflowDefinitionDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<WorkflowSummaryResp> items = new ArrayList<>(visible.size());
        for (WorkflowDefinitionDO row : visible) {
            items.add(converter.toSummary(row));
        }

        WorkflowListResp result = new WorkflowListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            WorkflowDefinitionDO last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public WorkflowDetailResp get(String publicId) {
        return converter.toDetail(requireExisting(publicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowDetailResp update(String publicId, WorkflowUpdateReq request) {
        if (request == null) {
            throw WorkflowSupport.invalid("工作流配置不能为空");
        }
        WorkflowDefinitionDO existing = requireExisting(publicId);
        if (request.getVersion() == null || !Objects.equals(existing.getVersion(), request.getVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "工作流配置已变更，请刷新后重试");
        }
        String name = request.getName() == null ? existing.getName() : WorkflowSupport.name(request.getName());
        requireAvailableName(name, existing.getId());

        WorkflowDefinition definition = existing.getDefinition();
        if (request.getDefinition() != null && !request.getDefinition().isBlank()) {
            definition = support.parse(request.getDefinition());
        }

        WorkflowDefinitionDO target = new WorkflowDefinitionDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setName(name);
        target.setDescription(request.getDescription() == null
                ? existing.getDescription() : WorkflowSupport.description(request.getDescription()));
        target.setDefinition(definition);
        target.setVersion(existing.getVersion() + 1);
        target.setStatus(WorkflowSupport.status(request.getStatus(), existing.getStatus()));
        try {
            int changed = workflows.update(target, new UpdateWrapper<WorkflowDefinitionDO>()
                    .eq("id", existing.getId()).eq("version", request.getVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "工作流配置已变更，请刷新后重试");
            }
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "工作流名称已被占用（包括已删除记录）", exception);
        }
        target.setCreatedAt(existing.getCreatedAt());
        return converter.toDetail(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        WorkflowDefinitionDO existing = requireExisting(publicId);
        if (workflows.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        }
    }

    @Override
    public WorkflowRunResp run(String publicId, WorkflowRunReq request, Long userId) {
        WorkflowDefinitionDO workflow = requireExisting(publicId);
        if (WorkflowSupport.STATUS_DISABLED.equals(workflow.getStatus())) {
            throw WorkflowSupport.invalid("工作流已停用，不能运行");
        }
        WorkflowDefinition definition = workflow.getDefinition();
        support.validate(definition);
        Map<String, Object> input = parseInput(request == null ? null : request.getInput());

        WorkflowRunDO run = new WorkflowRunDO();
        run.setDefinitionId(workflow.getId());
        run.setStatus(WorkflowSupport.RUN_RUNNING);
        run.setInput(input);
        run.setOutput(Map.of());
        run.setStepCount(0);
        runs.insert(run);

        WorkflowRunResp result = engine.execute(definition, input, userId);

        WorkflowRunDO update = new WorkflowRunDO();
        update.setId(run.getId());
        update.setStatus(result.status());
        update.setOutput(result.output() == null ? Map.of() : result.output());
        update.setStepCount(result.stepCount());
        update.setLatencyMs(result.latencyMs());
        if (result.errorMessage() != null) {
            update.setErrorMessage(result.errorMessage().length() > 1000
                    ? result.errorMessage().substring(0, 1000) : result.errorMessage());
        }
        runs.updateById(update);
        return result;
    }

    @Override
    public List<WorkflowRunSummaryResp> listRuns(String publicId, Integer limit) {
        WorkflowDefinitionDO workflow = requireExisting(publicId);
        int max = limit == null ? DEFAULT_RUN_LIMIT : Math.max(1, Math.min(limit, MAX_RUN_LIMIT));
        List<WorkflowRunDO> rows = runs.selectList(new QueryWrapper<WorkflowRunDO>()
                .eq("definition_id", workflow.getId())
                .orderByDesc("created_at", "id")
                .last("LIMIT " + max));
        List<WorkflowRunSummaryResp> result = new ArrayList<>(rows.size());
        for (WorkflowRunDO row : rows) {
            result.add(new WorkflowRunSummaryResp(row.getId(), row.getStatus(), row.getStepCount(),
                    row.getLatencyMs(), row.getErrorMessage(), row.getCreatedAt()));
        }
        return result;
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    private WorkflowDefinitionDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw WorkflowSupport.invalid("工作流标识不能为空");
        }
        WorkflowDefinitionDO entity = workflows.selectOne(new QueryWrapper<WorkflowDefinitionDO>()
                .eq("public_id", publicId.trim()));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
        }
        return entity;
    }

    /** 唯一键覆盖软删行：删掉的工作流名称不能复用。 */
    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = workflows.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "工作流名称已被占用（包括已删除记录）");
        }
    }

    /** 入参：JSON 文本 → 对象。 */
    private Map<String, Object> parseInput(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        try {
            return json.toMap(text);
        } catch (RuntimeException exception) {
            throw WorkflowSupport.invalid("入参不是合法 JSON 对象: " + exception.getMessage());
        }
    }

    private static int size(WorkflowListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
