package com.hify.agent.internal.service;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAssemblerTest {

    private static final String TOOL_ID = "01TOOL000000000000000000001";
    private static final String TOOL_ID_OFF = "01TOOL000000000000000000002";
    private static final String KB_ID = "01KB00000000000000000000001";

    @Mock AgentMapper agents;
    @Mock ModelService models;
    @Mock ToolDefinitionService tools;
    @Mock KnowledgeBaseService knowledgeBases;

    AgentAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new AgentAssembler(agents, models, tools, knowledgeBases);
    }

    static AgentDO entity(String status) {
        var entity = new AgentDO();
        entity.setId(1L);
        entity.setPublicId("01AGENT00000000000000000001");
        entity.setName("客服助手");
        entity.setModelId(7L);
        entity.setPromptTemplate("你是一个客服");
        entity.setToolIds(List.of(TOOL_ID, TOOL_ID_OFF));
        entity.setKnowledgeIds(List.of(KB_ID));
        entity.setParamConfig(Map.of("temperature", 0.3));
        entity.setStatus(status);
        entity.setAppType("chat");
        entity.setConfigVersion(1L);
        return entity;
    }

    static ModelSummaryResp model(boolean enabled) {
        var resp = new ModelSummaryResp();
        resp.setId(7L);
        resp.setPublicId("01MODEL0000000000000000001");
        resp.setDisplayName("通义千问");
        resp.setModelName("qwen-plus");
        resp.setModelType("chat");
        resp.setEnabled(enabled);
        return resp;
    }

    static ToolBinding binding(String publicId, boolean enabled) {
        return new ToolBinding(publicId, enabled,
                new ToolSpec(publicId, "工具", "描述", Map.of("type", "object"), Map.of(), "http"));
    }

    static KnowledgeBaseSummaryResp knowledge(String status) {
        var resp = new KnowledgeBaseSummaryResp();
        resp.setPublicId(KB_ID);
        resp.setName("产品手册");
        resp.setStatus(status);
        return resp;
    }

    @Test
    @DisplayName("组装：已发布的 Agent 拿到模型、工具与知识库")
    void assemblePublished() {
        when(agents.selectOne(any())).thenReturn(entity("published"));
        when(models.listByIds(anyList())).thenReturn(List.of(model(true)));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(
                List.of(binding(TOOL_ID, true), binding(TOOL_ID_OFF, true)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge("active")));

        var bundle = assembler.assemble("01AGENT00000000000000000001");

        assertThat(bundle.publicId()).isEqualTo("01AGENT00000000000000000001");
        assertThat(bundle.model().getModelName()).isEqualTo("qwen-plus");
        assertThat(bundle.tools()).hasSize(2);
        assertThat(bundle.knowledgeBases()).hasSize(1);
        assertThat(bundle.paramConfig().getTemperature()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("组装：草稿与停用的 Agent 一律拒绝")
    void assembleRejectsUnpublished() {
        for (String status : List.of("draft", "disabled")) {
            when(agents.selectOne(any())).thenReturn(entity(status));
            assertThatThrownBy(() -> assembler.assemble("01AGENT00000000000000000001"))
                    .isInstanceOf(BizException.class)
                    .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT))
                    .hasMessageContaining("未发布");
        }
    }

    @Test
    @DisplayName("组装：停用的工具与知识库不出现在结果里")
    void assembleSkipsUnavailable() {
        when(agents.selectOne(any())).thenReturn(entity("published"));
        when(models.listByIds(anyList())).thenReturn(List.of(model(true)));
        when(tools.listBindingsByPublicIds(anyList())).thenReturn(
                List.of(binding(TOOL_ID, true), binding(TOOL_ID_OFF, false)));
        when(knowledgeBases.listByPublicIds(anyList())).thenReturn(List.of(knowledge("disabled")));

        var bundle = assembler.assemble("01AGENT00000000000000000001");

        assertThat(bundle.tools()).hasSize(1);
        assertThat(bundle.knowledgeBases()).isEmpty();
    }

    @Test
    @DisplayName("组装：主模型已不存在或已停用时拒绝")
    void assembleRejectsBrokenModel() {
        when(agents.selectOne(any())).thenReturn(entity("published"));
        when(models.listByIds(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> assembler.assemble("01AGENT00000000000000000001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主模型已不存在");

        when(models.listByIds(anyList())).thenReturn(List.of(model(false)));
        assertThatThrownBy(() -> assembler.assemble("01AGENT00000000000000000001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("主模型已停用");
    }

    @Test
    @DisplayName("组装：Agent 不存在时报资源不存在")
    void assembleMissing() {
        when(agents.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> assembler.assemble("01AGENT00000000000000000001"))
                .isInstanceOf(BizException.class)
                .satisfies(thrown -> assertThat(((BizException) thrown).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
