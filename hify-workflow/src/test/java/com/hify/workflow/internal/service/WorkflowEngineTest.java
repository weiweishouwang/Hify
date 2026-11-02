package com.hify.workflow.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.knowledge.api.KnowledgeSearchService;
import com.hify.knowledge.api.SearchResult;
import com.hify.shared.json.JsonUtils;
import com.hify.tool.api.ToolBinding;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSpec;
import com.hify.workflow.api.WorkflowDefinition;
import com.hify.workflow.api.WorkflowRunResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 工作流执行：六种节点、模板插值、条件分支、汇聚与失败处理。 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock ChatService chat;
    @Mock ToolCatalog tools;
    @Mock ToolDefinitionService toolDefinitions;
    @Mock KnowledgeSearchService search;
    @Mock AgentCatalog agents;

    WorkflowSupport support;
    WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        JsonUtils json = new JsonUtils(new ObjectMapper());
        support = new WorkflowSupport(json);
        engine = new WorkflowEngine(chat, tools, toolDefinitions, search, agents,
                new TemplateResolver(), support);
    }

    /** 让模型回一句话。 */
    void modelAnswers(String answer) {
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onText(answer);
            listener.onFinish("stop");
            return null;
        }).when(chat).stream(any(), any());
    }

    WorkflowDefinition parse(String json) {
        return support.parse(json);
    }

    @Test
    @DisplayName("线性流程：llm 的输出进变量表，output 节点取它")
    void linearFlow() {
        modelAnswers("北京今天晴。");
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "天气：{{input.city}}", "output": "weather"},
                   {"id": "n2", "type": "output", "value": "{{weather}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);

        var result = engine.execute(definition, Map.of("city", "北京"), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.stepCount()).isEqualTo(2);
        assertThat(result.output()).containsEntry("result", "北京今天晴。");
        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chat).stream(captor.capture(), any());
        assertThat(captor.getValue().getMessages().getFirst().getContent()).isEqualTo("天气：北京");
    }

    @Test
    @DisplayName("条件成立走 then，不成立走 otherwise")
    void conditionalBranch() {
        modelAnswers("有问题");
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "问", "output": "answer"},
                   {"id": "n2", "type": "if", "left": "{{answer}}", "operator": "contains",
                    "right": "问题", "then": "n3", "otherwise": "n4"},
                   {"id": "n3", "type": "output", "value": "转人工", "output": "result"},
                   {"id": "n4", "type": "output", "value": "自动回复", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.output()).containsEntry("result", "转人工");
    }

    @Test
    @DisplayName("条件不成立走 otherwise")
    void conditionalBranchOtherwise() {
        modelAnswers("一切正常");
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "问", "output": "answer"},
                   {"id": "n2", "type": "if", "left": "{{answer}}", "operator": "contains",
                    "right": "问题", "then": "n3", "otherwise": "n4"},
                   {"id": "n3", "type": "output", "value": "转人工", "output": "result"},
                   {"id": "n4", "type": "output", "value": "自动回复", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.output()).containsEntry("result", "自动回复");
    }

    @Test
    @DisplayName("工具节点：调工具目录并把结果放进变量表")
    void toolNode() {
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "tool", "tool": "tool-1",
                    "args": {"no": "{{input.no}}"}, "output": "order"},
                   {"id": "n2", "type": "output", "value": "订单：{{order}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);
        ToolSpec spec = new ToolSpec("order_lookup", "查订单", "描述", Map.of(), Map.of(), "http");
        when(toolDefinitions.listBindingsByPublicIds(anyList()))
                .thenReturn(List.of(new ToolBinding("tool-1", true, spec)));
        when(tools.invoke(any(), anyString(), anyString(), any())).thenReturn(ToolResult.ok("A1 在配送中"));

        var result = engine.execute(definition, Map.of("no", "A1"), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.output()).containsEntry("result", "订单：A1 在配送中");
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(tools).invoke(any(), anyString(), anyString(), captor.capture());
        assertThat(captor.getValue()).containsEntry("no", "A1");
    }

    @Test
    @DisplayName("知识检索节点：输出分段正文列表")
    void knowledgeNode() {
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "knowledge", "knowledgeBase": "kb-1",
                    "query": "{{input.q}}", "topK": 2, "output": "docs"},
                   {"id": "n2", "type": "output", "value": "{{docs}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);
        when(search.search(anyString(), any())).thenReturn(List.of(
                new SearchResult(1L, 2L, "手册", 0, "第一条", 0.9),
                new SearchResult(3L, 2L, "手册", 1, "第二条", 0.8)));

        var result = engine.execute(definition, Map.of("q", "退货"), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        // 整个模板就是一个占位符 → 返回变量的原始值（列表），不是拼好的字符串
        assertThat(result.output().get("result")).isEqualTo(List.of("第一条", "第二条"));
    }

    @Test
    @DisplayName("Agent 节点：用 Agent 的模型与提示词调一次模型")
    void agentNode() {
        var model = new com.hify.ai.api.ModelSummaryResp();
        model.setId(7L);
        model.setPublicId("model-1");
        model.setModelName("qwen-plus");
        model.setEnabled(true);
        when(agents.assemble("agent-1")).thenReturn(new AgentBundle("agent-1", "客服", "published",
                "你是一个客服", model, List.of(), List.of(), null));
        modelAnswers("好的，我来处理。");

        var definition = parse("""
                {"nodes": [
                   {"id": "n1", "type": "agent", "agent": "agent-1", "input": "{{input.q}}", "output": "reply"},
                   {"id": "n2", "type": "output", "value": "{{reply}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);

        var result = engine.execute(definition, Map.of("q", "订单在哪"), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        assertThat(result.output()).containsEntry("result", "好的，我来处理。");
        var captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chat).stream(captor.capture(), any());
        List<ChatMessage> messages = captor.getValue().getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().getRole()).isEqualTo("system");
        assertThat(messages.getFirst().getContent()).isEqualTo("你是一个客服");
        assertThat(messages.get(1).getContent()).isEqualTo("订单在哪");
    }

    @Test
    @DisplayName("汇聚节点只跑一次")
    void mergeNodeRunsOnce() {
        modelAnswers("x");
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "a"},
                   {"id": "n2", "type": "llm", "prompt": "b", "output": "b"},
                   {"id": "n3", "type": "output", "value": "done", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}, {"from": "n1", "to": "n3"},
                              {"from": "n2", "to": "n3"}]}
                """);

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.status()).isEqualTo("succeeded");
        // n1、n2 各跑一次，汇聚的 n3 只跑一次 → 共 3 步
        assertThat(result.stepCount()).isEqualTo(3);
        verify(chat, org.mockito.Mockito.times(2)).stream(any(), any());
    }

    @Test
    @DisplayName("节点失败：整轮失败，错误信息说清是哪个节点")
    void nodeFailure() {
        modelAnswers("x");
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "a"},
                   {"id": "n2", "type": "tool", "tool": "tool-1", "output": "t"},
                   {"id": "n3", "type": "output", "value": "{{t}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}, {"from": "n2", "to": "n3"}]}
                """);
        ToolSpec spec = new ToolSpec("t", "查订单", "描述", Map.of(), Map.of(), "http");
        when(toolDefinitions.listBindingsByPublicIds(anyList()))
                .thenReturn(List.of(new ToolBinding("tool-1", true, spec)));
        when(tools.invoke(any(), anyString(), anyString(), any())).thenReturn(ToolResult.failure("上游挂了"));

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).contains("n2").contains("上游挂了");
    }

    @Test
    @DisplayName("模型调用失败：整轮失败")
    void modelFailure() {
        doAnswer(invocation -> {
            throw new com.hify.shared.exception.BizException(
                    com.hify.shared.exception.ErrorCode.UPSTREAM_ERROR, "上游超时");
        }).when(chat).stream(any(), any());
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "llm", "prompt": "a", "output": "a"},
                   {"id": "n2", "type": "output", "value": "{{a}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).contains("上游超时");
    }

    @Test
    @DisplayName("工具节点引用了停用的工具时失败")
    void disabledToolFails() {
        var definition = parse("""
                {"model": "m1", "nodes": [
                   {"id": "n1", "type": "tool", "tool": "tool-1", "output": "t"},
                   {"id": "n2", "type": "output", "value": "{{t}}", "output": "result"}
                 ], "edges": [{"from": "n1", "to": "n2"}]}
                """);
        ToolSpec spec = new ToolSpec("t", "查订单", "描述", Map.of(), Map.of(), "http");
        when(toolDefinitions.listBindingsByPublicIds(anyList()))
                .thenReturn(List.of(new ToolBinding("tool-1", false, spec)));

        var result = engine.execute(definition, Map.of(), 42L);

        assertThat(result.status()).isEqualTo("failed");
        assertThat(result.errorMessage()).contains("工具已停用");
        verify(tools, never()).invoke(any(), anyString(), anyString(), any());
    }
}
