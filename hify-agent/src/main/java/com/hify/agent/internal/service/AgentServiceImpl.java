package com.hify.agent.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hify.agent.api.AgentCreateReq;
import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentListQuery;
import com.hify.agent.api.AgentListResp;
import com.hify.agent.api.AgentOptions;
import com.hify.agent.api.AgentService;
import com.hify.agent.api.AgentStatus;
import com.hify.agent.api.AgentSummaryResp;
import com.hify.agent.api.AgentUpdateReq;
import com.hify.agent.internal.converter.AgentConverter;
import com.hify.agent.internal.entity.AgentDO;
import com.hify.agent.internal.mapper.AgentMapper;
import com.hify.ai.api.ModelListQuery;
import com.hify.ai.api.ModelService;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseListQuery;
import com.hify.knowledge.api.KnowledgeBaseService;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import com.hify.tool.api.ToolBinding;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolListQuery;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent 配置的增删改查与可选项聚合。
 *
 * <p><b>本模块不执行任何东西</b> —— 不调模型、不调工具、不检索知识库
 * （CLAUDE.md §2.1）。保存的是配置，组装出来的也只是零件清单。
 *
 * <p>事务只包数据库操作（CLAUDE.md §3.1 E5）。
 */
@Service
public class AgentServiceImpl implements AgentService {

    /** 可选项列表的单页上限，超过就截断并置 truncated 标志。 */
    private static final int OPTIONS_LIMIT = PageQuery.MAX_SIZE_ADMIN;

    /** 批量查引用时单次 IN 的最大个数，避免拼出超长 SQL。 */
    private static final int BATCH_CHUNK = 500;

    private final AgentMapper agents;
    private final ModelService models;
    private final ToolDefinitionService tools;
    private final KnowledgeBaseService knowledgeBases;
    private final AgentConverter converter;

    public AgentServiceImpl(AgentMapper agents, ModelService models, ToolDefinitionService tools,
                            KnowledgeBaseService knowledgeBases, AgentConverter converter) {
        this.agents = agents;
        this.models = models;
        this.tools = tools;
        this.knowledgeBases = knowledgeBases;
        this.converter = converter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailResp create(AgentCreateReq request) {
        if (request == null) {
            throw AgentSupport.invalid("Agent 配置不能为空");
        }
        String name = AgentSupport.name(request.getName());
        requireAvailableName(name, null);
        ModelSummaryResp model = requireChatModel(request.getModelPublicId());
        List<String> toolIds = AgentSupport.ids(request.getToolIds());
        List<String> knowledgeIds = AgentSupport.ids(request.getKnowledgeIds());
        requireToolsUsable(toolIds);
        requireKnowledgeUsable(knowledgeIds);

        AgentDO entity = new AgentDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setName(name);
        entity.setDescription(AgentSupport.description(request.getDescription()));
        entity.setModelId(model.getId());
        entity.setPromptTemplate(AgentSupport.prompt(request.getPromptTemplate()));
        entity.setToolIds(toolIds);
        entity.setKnowledgeIds(knowledgeIds);
        entity.setParamConfig(AgentSupport.paramConfig(request.getParamConfig()));
        entity.setStatus(AgentSupport.status(request.getStatus(), AgentStatus.DRAFT.code()));
        entity.setAppType(AgentSupport.APP_TYPE_CHAT);
        entity.setConfigVersion(AgentSupport.INITIAL_VERSION);
        try {
            agents.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 名称已被占用（包括已删除记录）", exception);
        }
        return converter.toDetail(entity, model, 0);
    }

    @Override
    public AgentListResp list(AgentListQuery query) {
        AgentListQuery effective = query == null ? new AgentListQuery() : query;
        var wrapper = new QueryWrapper<AgentDO>();
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

        List<AgentDO> rows = agents.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<AgentDO> visible = rows.subList(0, Math.min(rows.size(), size));

        Map<Long, ModelSummaryResp> modelById = loadModels(visible);
        Map<String, Boolean> toolEnabled = loadToolAvailability(visible);
        Map<String, Boolean> knowledgeActive = loadKnowledgeAvailability(visible);

        List<AgentSummaryResp> items = new ArrayList<>();
        for (AgentDO row : visible) {
            items.add(converter.toSummary(row, modelById.get(row.getModelId()),
                    unavailableCount(row, toolEnabled, knowledgeActive)));
        }

        AgentListResp result = new AgentListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            AgentDO last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public AgentDetailResp get(String publicId) {
        AgentDO row = requireExisting(publicId);
        ModelSummaryResp model = resolveModel(row);
        Map<String, Boolean> toolEnabled = loadToolAvailability(List.of(row));
        Map<String, Boolean> knowledgeActive = loadKnowledgeAvailability(List.of(row));
        return converter.toDetail(row, model, unavailableCount(row, toolEnabled, knowledgeActive));
    }

    @Override
    public AgentDetailResp getById(Long id) {
        if (id == null) {
            throw AgentSupport.invalid("Agent 标识不能为空");
        }
        AgentDO row = agents.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        ModelSummaryResp model = resolveModel(row);
        Map<String, Boolean> toolEnabled = loadToolAvailability(List.of(row));
        Map<String, Boolean> knowledgeActive = loadKnowledgeAvailability(List.of(row));
        return converter.toDetail(row, model, unavailableCount(row, toolEnabled, knowledgeActive));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentDetailResp update(String publicId, AgentUpdateReq request) {
        if (request == null) {
            throw AgentSupport.invalid("Agent 配置不能为空");
        }
        AgentDO existing = requireExisting(publicId);
        if (request.getConfigVersion() == null
                || !Objects.equals(existing.getConfigVersion(), request.getConfigVersion())) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 配置已变更，请刷新后重试");
        }

        String name = request.getName() == null ? existing.getName() : AgentSupport.name(request.getName());
        requireAvailableName(name, existing.getId());

        ModelSummaryResp model = existing.getModelId() == null ? null : resolveModel(existing);
        if (request.getModelPublicId() != null && !request.getModelPublicId().isBlank()) {
            model = requireChatModel(request.getModelPublicId());
        }

        List<String> toolIds = request.getToolIds() == null
                ? orEmpty(existing.getToolIds()) : AgentSupport.ids(request.getToolIds());
        List<String> knowledgeIds = request.getKnowledgeIds() == null
                ? orEmpty(existing.getKnowledgeIds()) : AgentSupport.ids(request.getKnowledgeIds());
        // 只校验这次显式提交过的绑定：没提交的沿用原值。
        // 否则「某个工具被停用」会把改个名字这类无关操作也一起卡住。
        if (request.getToolIds() != null) {
            requireToolsUsable(toolIds);
        }
        if (request.getKnowledgeIds() != null) {
            requireKnowledgeUsable(knowledgeIds);
        }

        AgentDO target = new AgentDO();
        target.setId(existing.getId());
        target.setPublicId(existing.getPublicId());
        target.setName(name);
        target.setDescription(request.getDescription() == null
                ? existing.getDescription() : AgentSupport.description(request.getDescription()));
        target.setModelId(model == null ? existing.getModelId() : model.getId());
        target.setPromptTemplate(request.getPromptTemplate() == null
                ? existing.getPromptTemplate() : AgentSupport.prompt(request.getPromptTemplate()));
        target.setToolIds(toolIds);
        target.setKnowledgeIds(knowledgeIds);
        target.setParamConfig(request.getParamConfig() == null
                ? existing.getParamConfig() : AgentSupport.paramConfig(request.getParamConfig()));
        target.setStatus(AgentSupport.status(request.getStatus(), existing.getStatus()));
        target.setAppType(existing.getAppType());
        target.setConfigVersion(existing.getConfigVersion() + 1);
        try {
            int changed = agents.update(target, new UpdateWrapper<AgentDO>()
                    .eq("id", existing.getId()).eq("config_version", request.getConfigVersion()));
            if (changed == 0) {
                throw new BizException(ErrorCode.CONFLICT, "Agent 配置已变更，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 名称已被占用（包括已删除记录）", exception);
        }
        target.setCreatedAt(existing.getCreatedAt());
        int unavailable = unavailableCount(target,
                loadToolAvailability(List.of(target)), loadKnowledgeAvailability(List.of(target)));
        return converter.toDetail(target, model, unavailable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        AgentDO existing = requireExisting(publicId);
        if (agents.deleteById(existing.getId()) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
    }

    @Override
    public AgentOptions options() {
        ModelListQuery modelQuery = new ModelListQuery();
        modelQuery.setModelType("chat");
        modelQuery.setEnabled(true);
        modelQuery.setSize(OPTIONS_LIMIT);
        var modelPage = models.list(modelQuery);

        ToolListQuery toolQuery = new ToolListQuery();
        toolQuery.setEnabled(true);
        toolQuery.setSize(OPTIONS_LIMIT);
        var toolPage = tools.list(toolQuery);

        KnowledgeBaseListQuery knowledgeQuery = new KnowledgeBaseListQuery();
        knowledgeQuery.setStatus("active");
        knowledgeQuery.setSize(OPTIONS_LIMIT);
        var knowledgePage = knowledgeBases.list(knowledgeQuery);

        List<AgentOptions.ModelOption> modelOptions = modelPage.getItems().stream()
                .map(model -> new AgentOptions.ModelOption(model.getPublicId(), model.getDisplayName(),
                        model.getModelName(), model.getProviderName()))
                .toList();
        List<AgentOptions.ToolOption> toolOptions = toolPage.getItems().stream()
                .map(tool -> new AgentOptions.ToolOption(tool.getPublicId(), tool.getName(),
                        tool.getSourceType(), tool.getDescription()))
                .toList();
        List<AgentOptions.KnowledgeOption> knowledgeOptions = knowledgePage.getItems().stream()
                .map(kb -> new AgentOptions.KnowledgeOption(kb.getPublicId(), kb.getName(),
                        kb.getDescription(), kb.getDocCount()))
                .toList();

        boolean truncated = Boolean.TRUE.equals(modelPage.getHasNext())
                || Boolean.TRUE.equals(toolPage.getHasNext())
                || Boolean.TRUE.equals(knowledgePage.getHasNext());
        return new AgentOptions(modelOptions, toolOptions, knowledgeOptions, truncated);
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    private AgentDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw AgentSupport.invalid("Agent 标识不能为空");
        }
        AgentDO entity = agents.selectOne(new QueryWrapper<AgentDO>().eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在");
        }
        return entity;
    }

    /** 唯一键覆盖软删行：删掉的 Agent 名称不能复用。 */
    private void requireAvailableName(String name, Long currentId) {
        Long ownerId = agents.findIdByName(name);
        if (ownerId != null && !ownerId.equals(currentId)) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 名称已被占用（包括已删除记录）");
        }
    }

    /**
     * 校验所选模型能用于对话。
     *
     * <p>必须是 chat 用途且已启用 —— 绑一个 embedding 模型，Agent 直接不可用。
     */
    private ModelSummaryResp requireChatModel(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw AgentSupport.invalid("请选择主模型");
        }
        ModelSummaryResp model = models.get(publicId.trim());
        if (!"chat".equals(model.getModelType())) {
            throw AgentSupport.invalid("所选模型不是对话模型");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw AgentSupport.invalid("所选模型已停用");
        }
        return model;
    }

    /** 绑定的工具必须存在且启用。 */
    private void requireToolsUsable(List<String> toolIds) {
        if (toolIds.isEmpty()) {
            return;
        }
        Map<String, Boolean> enabled = loadToolAvailabilityById(toolIds);
        for (String id : toolIds) {
            Boolean value = enabled.get(id);
            if (value == null) {
                throw AgentSupport.invalid("工具不存在: " + id);
            }
            if (!value) {
                throw AgentSupport.invalid("工具已停用: " + id);
            }
        }
    }

    /** 绑定的知识库必须存在且启用。 */
    private void requireKnowledgeUsable(List<String> knowledgeIds) {
        if (knowledgeIds.isEmpty()) {
            return;
        }
        Map<String, Boolean> active = loadKnowledgeAvailabilityById(knowledgeIds);
        for (String id : knowledgeIds) {
            Boolean value = active.get(id);
            if (value == null) {
                throw AgentSupport.invalid("知识库不存在: " + id);
            }
            if (!value) {
                throw AgentSupport.invalid("知识库已停用: " + id);
            }
        }
    }

    /** 当前页所有 Agent 绑定的工具 → 是否启用。 */
    private Map<String, Boolean> loadToolAvailability(List<AgentDO> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentDO row : rows) {
            ids.addAll(orEmpty(row.getToolIds()));
        }
        return loadToolAvailabilityById(new ArrayList<>(ids));
    }

    private Map<String, Boolean> loadToolAvailabilityById(List<String> publicIds) {
        Map<String, Boolean> result = new HashMap<>();
        for (List<String> chunk : chunk(publicIds)) {
            for (ToolBinding binding : tools.listBindingsByPublicIds(chunk)) {
                result.put(binding.publicId(), binding.enabled());
            }
        }
        return result;
    }

    /** 当前页所有 Agent 绑定的知识库 → 是否启用。 */
    private Map<String, Boolean> loadKnowledgeAvailability(List<AgentDO> rows) {
        Set<String> ids = new LinkedHashSet<>();
        for (AgentDO row : rows) {
            ids.addAll(orEmpty(row.getKnowledgeIds()));
        }
        return loadKnowledgeAvailabilityById(new ArrayList<>(ids));
    }

    private Map<String, Boolean> loadKnowledgeAvailabilityById(List<String> publicIds) {
        Map<String, Boolean> result = new HashMap<>();
        for (List<String> chunk : chunk(publicIds)) {
            for (KnowledgeBaseSummaryResp kb : knowledgeBases.listByPublicIds(chunk)) {
                result.put(kb.getPublicId(), "active".equals(kb.getStatus()));
            }
        }
        return result;
    }

    /** 绑定的引用里有多少当前不可用（工具 + 知识库）。 */
    private int unavailableCount(AgentDO row, Map<String, Boolean> toolEnabled,
                                 Map<String, Boolean> knowledgeActive) {
        int count = 0;
        for (String id : orEmpty(row.getToolIds())) {
            if (!Boolean.TRUE.equals(toolEnabled.get(id))) {
                count++;
            }
        }
        for (String id : orEmpty(row.getKnowledgeIds())) {
            if (!Boolean.TRUE.equals(knowledgeActive.get(id))) {
                count++;
            }
        }
        return count;
    }

    /** 批量解析当前页的模型，避免按行逐个查。 */
    private Map<Long, ModelSummaryResp> loadModels(List<AgentDO> rows) {
        Set<Long> ids = rows.stream().map(AgentDO::getModelId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return models.listByIds(ids).stream()
                .collect(Collectors.toMap(ModelSummaryResp::getId, Function.identity(), (left, right) -> left));
    }

    private ModelSummaryResp resolveModel(AgentDO row) {
        if (row.getModelId() == null) {
            return null;
        }
        List<ModelSummaryResp> found = models.listByIds(List.of(row.getModelId()));
        return found.isEmpty() ? null : found.getFirst();
    }

    /** 把 id 列表切成小批，避免一次拼出超长 IN。 */
    private static List<List<String>> chunk(List<String> values) {
        List<List<String>> chunks = new ArrayList<>();
        for (int index = 0; index < values.size(); index += BATCH_CHUNK) {
            chunks.add(values.subList(index, Math.min(values.size(), index + BATCH_CHUNK)));
        }
        return chunks;
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static int size(AgentListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
