package com.hify.conversation.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.mapper.CitationMapper;
import com.hify.conversation.internal.mapper.RunMapper;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.exception.BizException;
import com.hify.shared.json.JsonUtils;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 一轮对话的编排：工具循环、失败处理、轮次上限。 */
@ExtendWith(MockitoExtension.class)
class ChatOrchestratorTest {

    @Mock AgentCatalog agents;
    @Mock ChatService chat;
    @Mock ToolCatalog tools;
    @Mock MessageStore store;
    @Mock ContextBuilder contextBuilder;
    @Mock RunMapper runs;
    @Mock CitationMapper citations;

    ChatOrchestrator orchestrator;
    RecordingSink sink;

    /** 记录事件出口收到的内容。 */
    static class RecordingSink implements StreamEventSink {
        final List<String> events = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();
        boolean closed;

        @Override
        public void emit(String event, Object payload) {
            events.add(event);
            payloads.add(payload);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        String textOf(String event) {
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < events.size(); index++) {
                if (events.get(index).equals(event)) {
                    result.append((String) ((Map<?, ?>) payloads.get(index)).get("content"));
                }
            }
            return result.toString();
        }

        int countOf(String event) {
            return (int) events.stream().filter(event::equals).count();
        }
    }

    @BeforeEach
    void setUp() {
        JsonUtils json = new JsonUtils(new ObjectMapper());
        orchestrator = new ChatOrchestrator(agents, chat, tools, store, contextBuilder,
                runs, citations, json);
        sink = new RecordingSink();
        lenient().when(store.appendUser(any(), anyString())).thenReturn(10L);
        lenient().when(store.appendAssistant(any(), anyString(), any(), any(), any())).thenReturn(11L);
        lenient().when(store.appendTool(any(), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenReturn(12L);
        lenient().when(contextBuilder.build(any(), any(), anyString()))
                .thenReturn(new ContextBuilder.BuiltContext(
                        List.of(ChatMessage.user("订单在哪")), List.of()));
        when(agents.assembleById(any())).thenReturn(bundle());
    }

    AgentBundle bundle() {
        var model = new ModelSummaryResp();
        model.setId(7L);
        model.setPublicId("01MODEL0000000000000000001");
        model.setModelName("qwen-plus");
        model.setModelType("chat");
        model.setEnabled(true);
        List<ToolSpec> tools = List.of(new ToolSpec("current_time", "当前时间", "取当前时间",
                Map.of("type", "object"), Map.of(), "builtin"));
        return new AgentBundle("01AGENT00000000000000000001", "客服", "published", "你是客服",
                model, tools, List.<KnowledgeBaseSummaryResp>of(), null);
    }

    ConversationDO session() {
        var session = new ConversationDO();
        session.setId(1L);
        session.setPublicId("01SESSION00000000000000001");
        session.setAgentId(3L);
        session.setUserId(42L);
        session.setTitle("");
        session.setMessageCount(0);
        session.setStatus("active");
        return session;
    }

    void givenSession() {
        // 会话已由调用方（HTTP 层）校验归属，编排器直接拿
    }

    @Test
    @DisplayName("没有工具调用：一轮模型调用后收尾")
    void singleTurn() {
        givenSession();
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onText("您好");
            listener.onUsage(ChatUsage.of(10, 5));
            listener.onFinish("stop");
            return null;
        }).when(chat).stream(any(), any());

        orchestrator.run(session(), "订单在哪", 42L, sink);

        assertThat(sink.textOf("message")).isEqualTo("您好");
        assertThat(sink.countOf("done")).isEqualTo(1);
        verify(store).appendUser(1L, "订单在哪");
        verify(store).appendAssistant(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("您好"), org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq("stop"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("模型要工具：执行 → 回灌 → 再调一次模型")
    void toolRound() {
        givenSession();
        AtomicInteger turns = new AtomicInteger();
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            if (turns.getAndIncrement() == 0) {
                listener.onText("查一下");
                listener.onToolCall(new ChatToolCall("call_1", "current_time", "{}"));
                listener.onFinish("tool_calls");
            } else {
                listener.onText("现在 2026 年");
                listener.onFinish("stop");
            }
            return null;
        }).when(chat).stream(any(), any());
        when(tools.invoke(any(), anyString(), anyString(), any())).thenReturn(ToolResult.ok("2026-11-01"));

        orchestrator.run(session(), "现在几点", 42L, sink);

        assertThat(sink.countOf("tool_call")).isEqualTo(1);
        assertThat(sink.countOf("tool_result")).isEqualTo(1);
        assertThat(sink.countOf("done")).isEqualTo(1);
        assertThat(sink.textOf("message")).contains("查一下").contains("现在 2026 年");
        verify(store).appendTool(1L, "call_1", "当前时间", "builtin:current_time", "{}",
                "2026-11-01", null);
        verify(chat, org.mockito.Mockito.times(2)).stream(any(), any());
    }

    @Test
    @DisplayName("工具失败：失败原因回灌给模型，不中断整轮")
    void toolFailureDoesNotBreakTheTurn() {
        givenSession();
        AtomicInteger turns = new AtomicInteger();
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            if (turns.getAndIncrement() == 0) {
                listener.onToolCall(new ChatToolCall("call_1", "current_time", "{}"));
                listener.onFinish("tool_calls");
            } else {
                listener.onText("工具坏了");
                listener.onFinish("stop");
            }
            return null;
        }).when(chat).stream(any(), any());
        when(tools.invoke(any(), anyString(), anyString(), any()))
                .thenReturn(ToolResult.failure("上游不可用"));

        orchestrator.run(session(), "现在几点", 42L, sink);

        assertThat(sink.countOf("done")).isEqualTo(1);
        verify(store).appendTool(1L, "call_1", "当前时间", "builtin:current_time", "{}", "", "上游不可用");
    }

    @Test
    @DisplayName("工具轮次超限：以 tool_limit 收尾，不再调模型")
    void toolRoundLimit() {
        givenSession();
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onToolCall(new ChatToolCall("call_x", "current_time", "{}"));
            listener.onFinish("tool_calls");
            return null;
        }).when(chat).stream(any(), any());
        when(tools.invoke(any(), anyString(), anyString(), any())).thenReturn(ToolResult.ok("ok"));

        orchestrator.run(session(), "现在几点", 42L, sink);

        // 5 轮工具 + 第 6 轮发现超限，不再执行工具
        verify(tools, org.mockito.Mockito.times(ConversationSupport.MAX_TOOL_ROUNDS))
                .invoke(any(), anyString(), anyString(), any());
        assertThat(sink.countOf("done")).isEqualTo(1);
        assertThat(sink.events).contains("done");
    }

    @Test
    @DisplayName("模型报错：发 error 事件并把运行记成失败")
    void modelFailure() {
        givenSession();
        doAnswer(invocation -> {
            throw new BizException(com.hify.shared.exception.ErrorCode.UPSTREAM_ERROR, "上游超时");
        }).when(chat).stream(any(), any());

        assertThatThrownBy(() -> orchestrator.run(session(), "在吗", 42L, sink))
                .isInstanceOf(BizException.class);
        assertThat(sink.countOf("error")).isEqualTo(1);
        assertThat(sink.countOf("done")).isZero();
    }

    @Test
    @DisplayName("引用写到助手消息上")
    void citationsArePersisted() {
        givenSession();
        var citation = new ContextBuilder.Citation(5L, 100L, 200L, 3, "产品手册", "订单规则", 0.87);
        when(contextBuilder.build(any(), any(), anyString()))
                .thenReturn(new ContextBuilder.BuiltContext(List.of(ChatMessage.user("订单在哪")),
                        List.of(citation)));
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onText("好的");
            listener.onFinish("stop");
            return null;
        }).when(chat).stream(any(), any());

        orchestrator.run(session(), "订单在哪", 42L, sink);

        verify(citations).insert(any(com.hify.conversation.internal.entity.CitationDO.class));
    }
}
