package com.hify.agent.internal.service;

import com.hify.agent.api.AgentCreateReq;
import com.hify.agent.api.AgentListQuery;
import com.hify.agent.api.AgentParamConfig;
import com.hify.agent.api.AgentUpdateReq;
import com.hify.agent.internal.converter.AgentConverter;
import com.hify.agent.internal.entity.AgentDO;
import com.hify.agent.internal.mapper.AgentMapper;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.ai.api.ModelService;
import com.hify.knowledge.api.KnowledgeBaseService;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolBinding;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    private static final String TOOL_ID = "01TOOL000000000000000000001";
    private static final String TOOL_ID_DISABLED = "01TOOL000000000000000000002";
    private static final String KB_ID = "01KB00000000000000000000001";
    private static final String MODEL_PUBLIC_ID = "01MODEL0000000000000000001";

    @Mock AgentMapper agents;
    @Mock ModelService models;
    @Mock ToolDefinitionService tools;
    @Mock KnowledgeBaseService knowledgeBases;

    AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentServiceImpl(agents, models, tools, knowledgeBases, new AgentConverter());
        lenient().when(agents.findIdByName(anyString())).thenReturn(null);
    }

    static ModelSummaryResp model(String publicId, String type, boolean enabled) {
        var resp = new ModelSummaryResp();
        resp.setId(7L);
        resp.setPublicId(publicId);
        resp.setDisplayName("通义千问");
        resp.setModelName("qwen-plus");
        resp.setModelType(type);
        resp.setEnabled(enabled);
        return resp;
    }

    static ToolBinding binding(String publicId, boolean enabled) {
        return new ToolBinding(publicId, enabled,
                new ToolSpec(publicId, "工具", "描述", Map.of("type", "object"), Map.of(), "http"));
    }

    static KnowledgeBaseSummaryResp knowledge(String publicId, String status) {
        var resp = new KnowledgeBaseSummaryResp();
        resp.setPublicId(publicId);
        resp.setName("产品手册");
        resp.setStatus(status);
        resp.setDocCount(3);
        return resp;
    }

    static AgentCreateReq createRequest() {
        var req = new AgentCreateReq();
        req.setName("  客服助手 ");
        req.setDescription("处理售后咨询");
        req.setModelPublicId(MODEL_PUBLIC_ID);
        req.setPromptTemplate("你是一个客服");
        req.setToolIds(List.of(TOOL_ID));
        req.setKnowledgeIds(List.of(KB_ID));
        return req;
    }

    static AgentDO entity() {
        var entity = new AgentDO();
        entity.setId(1L);
        entity.setPublicId("01AGENT00000000000000000001");
        entity.setName("客服助手");
        entity.setDescription("处理售后咨询");
        entity.setModelId(7L);
        entity.setPromptTemplate("你是一个客服");
        entity.setToolIds(List.of(TOOL_ID));
        entity.setKnowledgeIds(List.of(KB_ID));
        entity.setParamConfig(Map.of("temperature", 0.3));
        entity.setStatus("draft");
        entity.setAppType("chat");
        entity.setConfigVersion(1L);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("创建：名称去空格、默认草稿、模型主键落库、绑定项去重")
    void createNormalizesInput() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", true));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, true)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "active")));

        var request = createRequest();
        request.setToolIds(List.of(TOOL_ID, TOOL_ID, " " + TOOL_ID + " "));
        var resp = service.create(request);

        assertThat(resp.getName()).isEqualTo("客服助手");
        assertThat(resp.getStatus()).isEqualTo("draft");
        assertThat(resp.getModelPublicId()).isEqualTo(MODEL_PUBLIC_ID);
        assertThat(resp.getToolIds()).containsExactly(TOOL_ID);

        var captor = ArgumentCaptor.forClass(AgentDO.class);
        verify(agents).insert(captor.capture());
        assertThat(captor.getValue().getModelId()).isEqualTo(7L);
        assertThat(captor.getValue().getToolIds()).containsExactly(TOOL_ID);
        assertThat(captor.getValue().getAppType()).isEqualTo("chat");
        assertThat(captor.getValue().getConfigVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("创建：模型不是对话模型时拒绝")
    void createRejectsNonChatModel() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "embedding", true));
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR))
                .hasMessageContaining("不是对话模型");
    }

    @Test
    @DisplayName("创建：模型已停用时拒绝")
    void createRejectsDisabledModel() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", false));
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("创建：绑定的工具不存在时点名是哪个")
    void createRejectsMissingTool() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", true));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工具不存在")
                .hasMessageContaining(TOOL_ID);
    }

    @Test
    @DisplayName("创建：绑定的工具已停用时拒绝")
    void createRejectsDisabledTool() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", true));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, false)));
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工具已停用");
    }

    @Test
    @DisplayName("创建：绑定的知识库已停用时拒绝")
    void createRejectsDisabledKnowledge() {
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", true));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, true)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "disabled")));
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("知识库已停用");
    }

    @Test
    @DisplayName("创建：名称已被占用（含已删除记录）时冲突")
    void createRejectsDuplicateName() {
        when(agents.findIdByName("客服助手")).thenReturn(99L);
        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("已被占用");
    }

    @Test
    @DisplayName("参数区越界时拒绝保存")
    void createRejectsOutOfRangeParams() {
        var request = createRequest();
        var params = new AgentParamConfig();
        params.setTemperature(3.5);
        request.setParamConfig(params);
        when(models.get(MODEL_PUBLIC_ID)).thenReturn(model(MODEL_PUBLIC_ID, "chat", true));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, true)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "active")));
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("temperature");
    }

    @Test
    @DisplayName("更新：配置版本不匹配时冲突")
    void updateRejectsStaleVersion() {
        when(agents.selectOne(any())).thenReturn(entity());
        var request = new AgentUpdateReq();
        request.setName("新名字");
        request.setConfigVersion(9L);
        assertThatThrownBy(() -> service.update("01AGENT00000000000000000001", request))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT))
                .hasMessageContaining("配置已变更");
    }

    @Test
    @DisplayName("更新：乐观锁命中 0 行时冲突")
    void updateRejectsLostRace() {
        when(agents.selectOne(any())).thenReturn(entity());
        when(agents.update(any(AgentDO.class), any())).thenReturn(0);
        var request = new AgentUpdateReq();
        request.setName("新名字");
        request.setConfigVersion(1L);
        assertThatThrownBy(() -> service.update("01AGENT00000000000000000001", request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("配置已变更");
    }

    @Test
    @DisplayName("更新：没提交绑定项时，不因工具被停用而失败")
    void updateKeepsBrokenBindingsWhenNotSubmitted() {
        when(agents.selectOne(any())).thenReturn(entity());
        when(agents.update(any(AgentDO.class), any())).thenReturn(1);
        when(models.listByIds(anyCollection())).thenReturn(List.of(model(MODEL_PUBLIC_ID, "chat", true)));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, false)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "active")));

        var request = new AgentUpdateReq();
        request.setName("新名字");
        request.setConfigVersion(1L);
        var resp = service.update("01AGENT00000000000000000001", request);

        assertThat(resp.getName()).isEqualTo("新名字");
        assertThat(resp.getUnavailableCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("删除：不存在时报资源不存在")
    void deleteMissing() {
        when(agents.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.delete("01AGENT00000000000000000001"))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("删除：按主键逻辑删除")
    void deleteSoftDeletes() {
        when(agents.selectOne(any())).thenReturn(entity());
        when(agents.deleteById(anyLong())).thenReturn(1);
        service.delete("01AGENT00000000000000000001");
        verify(agents).deleteById(1L);
    }

    @Test
    @DisplayName("列表：游标分页，多取一条判断有没有下一页")
    void listPaginatesByCursor() {
        var first = entity();
        var second = entity();
        second.setId(2L);
        second.setName("第二个");
        second.setCreatedAt(LocalDateTime.of(2026, 11, 1, 11, 0));
        when(agents.selectList(any())).thenReturn(List.of(first, second));
        when(models.listByIds(anyCollection())).thenReturn(List.of(model(MODEL_PUBLIC_ID, "chat", true)));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, true)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "active")));

        var query = new AgentListQuery();
        query.setSize(1);
        var resp = service.list(query);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isTrue();
        assertThat(resp.getNextCursor()).isNotBlank();
    }

    @Test
    @DisplayName("列表：绑定的工具被停用时计入不可用数")
    void listCountsUnavailableReferences() {
        when(agents.selectList(any())).thenReturn(List.of(entity()));
        when(models.listByIds(anyCollection())).thenReturn(List.of(model(MODEL_PUBLIC_ID, "chat", true)));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(List.of(binding(TOOL_ID, false)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge(KB_ID, "active")));

        var resp = service.list(new AgentListQuery());
        assertThat(resp.getItems().getFirst().getUnavailableCount()).isEqualTo(1);
        assertThat(resp.getItems().getFirst().getToolCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：状态与关键字筛选落到查询条件上")
    void listFiltersByStatusAndKeyword() {
        when(agents.selectList(any())).thenReturn(List.of());
        var query = new AgentListQuery();
        query.setStatus("published");
        query.setKeyword("客服");
        service.list(query);

        var captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(agents).selectList(captor.capture());
        assertThat(captor.getValue().getTargetSql()).contains("status");
        assertThat(captor.getValue().getTargetSql()).contains("name");
    }
}
