package com.hify.conversation.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.agent.api.AgentBundle;
import com.hify.agent.api.AgentCatalog;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.conversation.internal.entity.CitationDO;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.entity.RunDO;
import com.hify.conversation.internal.mapper.CitationMapper;
import com.hify.conversation.internal.mapper.RunMapper;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.context.TraceContext;
import com.hify.shared.json.JsonUtils;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一轮对话的编排：读 Agent 配置 → 检索 → 调模型 → 要工具就执行并回灌 → 直到模型收手。
 *
 * <p><b>编排层</b>：组合逻辑都在这里（CLAUDE.md §2.1）。
 * ai 只管调模型，tool 只管执行工具，knowledge 只管检索，agent 只管组装配置。
 *
 * <p><b>跑在 llmExecutor 上</b> —— 上游调用与工具执行都是阻塞的，
 * 不能占 Tomcat 工作线程（CLAUDE.md §3.5）。
 *
 * <p><b>每一轮模型调用各留一条助手消息</b>：要工具的那轮正文留自己的、
 * 工具结果单独留一条。这样历史与当轮上下文完全一致 ——
 * 回放历史时重建出来的消息，跟当初发出去的一模一样。
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final AgentCatalog agents;
    private final ChatService chat;
    private final ToolCatalog tools;
    private final MessageStore store;
    private final ContextBuilder contextBuilder;
    private final RunMapper runs;
    private final CitationMapper citations;
    private final CurrentUserService currentUsers;
    private final JsonUtils json;

    public ChatOrchestrator(AgentCatalog agents, ChatService chat,
                            ToolCatalog tools, MessageStore store, ContextBuilder contextBuilder,
                            RunMapper runs, CitationMapper citations, CurrentUserService currentUsers,
                            JsonUtils json) {
        this.agents = agents;
        this.chat = chat;
        this.tools = tools;
        this.store = store;
        this.contextBuilder = contextBuilder;
        this.runs = runs;
        this.citations = citations;
        this.currentUsers = currentUsers;
        this.json = json;
    }

    /**
     * 跑一轮对话。
     *
     * @param session  会话（调用方已校验归属）
     * @param userInput 用户输入
     * @param sink      事件出口
     */
    public void run(ConversationDO session, String userInput, StreamEventSink sink) {
        long startNanos = System.nanoTime();
        String sessionPublicId = session.getPublicId();
        String content = ConversationSupport.content(userInput);
        AgentBundle bundle = agents.assembleById(session.getAgentId());
        ToolFunctionNames toolNames = ToolFunctionNames.of(bundle);

        store.appendUser(session.getId(), content);
        RunDO run = startRun(session, bundle, content);
        ContextBuilder.BuiltContext context = contextBuilder.build(session.getId(), bundle, content);

        StringBuilder accumulated = new StringBuilder();
        List<ChatMessage> working = new ArrayList<>(context.messages());
        int toolCallCount = 0;
        int promptTokens = 0;
        int completionTokens = 0;
        String finishReason = "stop";
        String notice = null;
        Long assistantMessageId = null;

        try {
            int round = 0;
            int turnIndex = 0;
            while (true) {
                // 每一轮模型调用前发一个 turn 事件：前端据此另起一条助手消息，
                // 这样流式看到的分段与刷新后的历史一致
                sink.emit("turn", payload("index", turnIndex++));
                ModelTurn turn = callModel(bundle, toolNames, working, sink);
                accumulated.append(turn.text());
                promptTokens += turn.promptTokens();
                completionTokens += turn.completionTokens();

                if (turn.toolCalls().isEmpty()) {
                    finishReason = turn.finishReason();
                    assistantMessageId = store.appendAssistant(session.getId(), turn.text(),
                            turn.completionTokens(), finishReason, turn.latencyMs());
                    break;
                }
                if (round >= ConversationSupport.MAX_TOOL_ROUNDS) {
                    finishReason = "tool_limit";
                    notice = "已达到工具调用轮次上限（" + ConversationSupport.MAX_TOOL_ROUNDS + " 轮）";
                    assistantMessageId = store.appendAssistant(session.getId(), turn.text(),
                            turn.completionTokens(), finishReason, turn.latencyMs());
                    break;
                }
                round++;
                store.appendAssistant(session.getId(), turn.text(), turn.completionTokens(),
                        "tool_calls", turn.latencyMs());

                List<ChatMessage> toolMessages = new ArrayList<>();
                for (ChatToolCall call : turn.toolCalls()) {
                    toolCallCount++;
                    emitToolCall(sink, call);
                    ToolOutcome outcome = executeTool(call, toolNames);
                    store.appendTool(session.getId(), call.id(), outcome.displayName(), outcome.toolKey(),
                            outcome.arguments(), outcome.result(), outcome.error());
                    emitToolResult(sink, call, outcome);
                    toolMessages.add(ChatMessage.tool(call.id(), call.name(),
                            outcome.error() != null ? outcome.error() : outcome.result()));
                }
                ChatMessage assistant = ChatMessage.assistant(turn.text());
                assistant.setToolCalls(turn.toolCalls());
                working.add(assistant);
                working.addAll(toolMessages);
                if (sink.isClosed()) {
                    log.info("客户端已断开，停止本轮对话 session={}", sessionPublicId);
                    return;
                }
            }

            writeCitations(session.getId(), assistantMessageId, context.citations());
            finishRun(run, accumulated.toString(), finishReason, toolCallCount, promptTokens,
                    completionTokens, elapsed(startNanos), null);
            store.touchSession(session.getId(), titleOf(session, content));
            sink.emit("done", notice == null
                    ? payload("finishReason", finishReason)
                    : payload("finishReason", finishReason, "message", notice));
        } catch (RuntimeException exception) {
            log.warn("对话失败 session={} reason={}", sessionPublicId, exception.getClass().getSimpleName());
            finishRun(run, accumulated.toString(), "error", toolCallCount, promptTokens,
                    completionTokens, elapsed(startNanos), messageOf(exception));
            store.touchSession(session.getId(), titleOf(session, content));
            sink.emit("error", payload("code", 50000, "message", messageOf(exception)));
            throw exception;
        }
    }

    /** 调一次模型，把增量吐给事件出口。 */
    private ModelTurn callModel(AgentBundle bundle, ToolFunctionNames toolNames,
                                List<ChatMessage> working, StreamEventSink sink) {
        ChatRequest request = new ChatRequest();
        request.setModelPublicId(bundle.model().getPublicId());
        request.setMessages(working);
        request.setTools(toolNames.chatTools());
        request.setParams(params(bundle));

        long startNanos = System.nanoTime();
        StringBuilder text = new StringBuilder();
        List<ChatToolCall> toolCalls = new ArrayList<>();
        String[] finishReason = {"stop"};
        int[] promptTokens = {0};
        int[] completionTokens = {0};

        chat.stream(request, new ChatListener() {
            @Override
            public void onText(String delta) {
                text.append(delta);
                sink.emit("message", payload("content", delta));
            }

            @Override
            public void onToolCall(ChatToolCall call) {
                toolCalls.add(call);
            }

            @Override
            public void onUsage(ChatUsage usage) {
                promptTokens[0] = usage.promptTokens() == null ? 0 : usage.promptTokens();
                completionTokens[0] = usage.completionTokens() == null ? 0 : usage.completionTokens();
            }

            @Override
            public void onFinish(String reason) {
                finishReason[0] = reason;
            }
        });
        return new ModelTurn(text.toString(), List.copyOf(toolCalls), finishReason[0],
                promptTokens[0], completionTokens[0], elapsed(startNanos));
    }

    /**
     * 执行一次工具调用。
     *
     * <p><b>工具失败不中断整轮</b> —— 失败原因作为工具结果回灌给模型，
     * 让模型自己决定怎么办。工具一失败就把对话掐断，用户会卡在那儿。
     */
    private ToolOutcome executeTool(ChatToolCall call, ToolFunctionNames toolNames) {
        ToolSpec spec = toolNames.specOf(call.name());
        if (spec == null) {
            String error = "工具不存在: " + call.name();
            return ToolOutcome.failed(call, call.name(), error);
        }
        Map<String, Object> args = parseArguments(call.arguments());
        ToolInvokeContext context = ToolInvokeContext.of(currentUsers.currentUserId(), TraceContext.get());
        try {
            ToolResult result = tools.invoke(context, spec.sourceType(), spec.toolKey(), args);
            if (result.success()) {
                return ToolOutcome.ok(call, spec, result.content());
            }
            String error = result.errorMessage() == null ? "工具执行失败" : result.errorMessage();
            return ToolOutcome.failed(call, spec, error);
        } catch (RuntimeException exception) {
            log.warn("工具执行失败 tool={} reason={}", call.name(), exception.getClass().getSimpleName());
            return ToolOutcome.failed(call, spec, messageOf(exception));
        }
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = json.mapper().readTree(arguments);
            if (node == null || !node.isObject()) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), toPlain(entry.getValue())));
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Object toPlain(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(item -> list.add(toPlain(item)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), toPlain(entry.getValue())));
            return map;
        }
        return node.asText();
    }

    /** 建运行记录。 */
    private RunDO startRun(ConversationDO session, AgentBundle bundle, String input) {
        RunDO run = new RunDO();
        run.setSessionId(session.getId());
        run.setAgentId(session.getAgentId());
        run.setModelId(bundle.model() == null ? null : bundle.model().getId());
        run.setProviderId(bundle.model() == null ? null : bundle.model().getProviderId());
        run.setStatus(ConversationSupport.RUN_RUNNING);
        run.setInputText(input);
        run.setToolCallCount(0);
        run.setPromptTokens(0);
        run.setCompletionTokens(0);
        run.setTotalTokens(0);
        runs.insert(run);
        return run;
    }

    /** 结束运行记录。 */
    private void finishRun(RunDO run, String outputText, String finishReason, int toolCallCount,
                           int promptTokens, int completionTokens, int latencyMs, String errorMessage) {
        RunDO update = new RunDO();
        update.setId(run.getId());
        update.setStatus(errorMessage == null
                ? ConversationSupport.RUN_SUCCEEDED : ConversationSupport.RUN_FAILED);
        update.setOutputText(outputText);
        update.setToolCallCount(toolCallCount);
        update.setPromptTokens(promptTokens);
        update.setCompletionTokens(completionTokens);
        update.setTotalTokens(promptTokens + completionTokens);
        update.setLatencyMs(latencyMs);
        if (errorMessage != null) {
            update.setErrorCode(50000);
            update.setErrorMessage(errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000) : errorMessage);
        }
        runs.updateById(update);
        log.info("对话完成 session={} finishReason={} tools={} tokens={} cost={}ms",
                run.getSessionId(), finishReason, toolCallCount, promptTokens + completionTokens, latencyMs);
    }

    /** 写引用。 */
    private void writeCitations(Long sessionId, Long messageId, List<ContextBuilder.Citation> used) {
        if (messageId == null || used == null || used.isEmpty()) {
            return;
        }
        for (ContextBuilder.Citation citation : used) {
            CitationDO row = new CitationDO();
            row.setSessionId(sessionId);
            row.setMessageId(messageId);
            row.setChunkId(citation.chunkId());
            row.setDocId(citation.docId());
            row.setChunkIndex(citation.chunkIndex());
            row.setKbId(citation.kbId());
            row.setQuoteText(citation.quoteText());
            row.setScore(citation.score() == null ? null : BigDecimal.valueOf(citation.score()));
            citations.insert(row);
        }
    }

    /** 会话标题：没设过就用首条用户消息的前若干字。 */
    private String titleOf(ConversationDO session, String firstInput) {
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return null;
        }
        return ConversationSupport.deriveTitle(firstInput);
    }

    private void emitToolCall(StreamEventSink sink, ChatToolCall call) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", call.id());
        payload.put("name", call.name());
        payload.put("arguments", call.arguments());
        sink.emit("tool_call", payload);
    }

    private void emitToolResult(StreamEventSink sink, ChatToolCall call, ToolOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", call.id());
        payload.put("name", outcome.displayName());
        payload.put("arguments", outcome.arguments());
        payload.put("result", outcome.result());
        payload.put("error", outcome.error());
        sink.emit("tool_result", payload);
    }

    private static Map<String, Object> params(AgentBundle bundle) {
        var config = bundle.paramConfig();
        Map<String, Object> params = new LinkedHashMap<>();
        if (config == null) {
            return params;
        }
        if (config.getTemperature() != null) {
            params.put("temperature", config.getTemperature());
        }
        if (config.getMaxTokens() != null) {
            params.put("max_tokens", config.getMaxTokens());
        }
        if (config.getTopP() != null) {
            params.put("top_p", config.getTopP());
        }
        return params;
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private static int elapsed(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000L);
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "对话失败" : message;
    }

    /**
     * 一次模型调用的结果。
     *
     * @param text             本轮正文
     * @param toolCalls        本轮要调的工具
     * @param finishReason     结束原因
     * @param promptTokens     输入 token
     * @param completionTokens 输出 token
     * @param latencyMs        耗时
     */
    private record ModelTurn(String text, List<ChatToolCall> toolCalls, String finishReason,
                             int promptTokens, int completionTokens, int latencyMs) {
    }

    /**
     * 一次工具执行的结果。
     *
     * @param call        模型给的调用
     * @param displayName 工具显示名
     * @param toolKey     工具标识（来源:工具标识），回放历史时靠它找回函数名
     * @param arguments   入参
     * @param result      结果
     * @param error       失败原因
     */
    private record ToolOutcome(ChatToolCall call, String displayName, String toolKey,
                               String arguments, String result, String error) {

        static ToolOutcome ok(ChatToolCall call, ToolSpec spec, String result) {
            return new ToolOutcome(call, spec.name(), ToolFunctionNames.toolKey(spec),
                    call.arguments(), result == null ? "" : result, null);
        }

        static ToolOutcome failed(ChatToolCall call, ToolSpec spec, String error) {
            return new ToolOutcome(call, spec.name(), ToolFunctionNames.toolKey(spec),
                    call.arguments(), "", error);
        }

        static ToolOutcome failed(ChatToolCall call, String displayName, String error) {
            return new ToolOutcome(call, displayName, "", call.arguments(), "", error);
        }
    }
}
