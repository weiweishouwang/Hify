package com.hify.ai.internal.spi;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatTool;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的对话实现：{@code POST {base}/chat/completions}。
 *
 * <p>这一档覆盖 OpenAI 官方与全部第三方兼容端点 —— 请求体、工具定义、流式帧结构
 * 完全一致，彼此只差 base_url。
 *
 * <p><b>工具参数是分片到达的</b>：{@code delta.tool_calls[].function.arguments}
 * 是一段 JSON 字符串的增量，这里用 {@link ToolCallAccumulator} 拼完整再回调。
 */
@Component
public class OpenAiChatAdapter extends AbstractProviderAdapter implements ChatProtocolAdapter {

    /** OpenAI 用 {@code [DONE]} 标记流结束。 */
    private static final String DONE_MARKER = "[DONE]";

    public OpenAiChatAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.OPENAI;
    }

    @Override
    public void stream(ModelProviderDO provider, String modelName, ChatRequest request, ChatListener listener) {
        String url = baseUrl(provider) + "/chat/completions";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Accept", "text/event-stream");
        String key = decryptKey(authConfig(provider).get(ProviderConfigSupport.API_KEY));
        if (key != null) {
            headers.put("Authorization", "Bearer " + key);
        }

        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        String[] finishReason = {null};
        ChatUsage[] usage = {null};

        http.stream(url, headers, buildBody(modelName, request), (eventType, data) -> {
            if (data == null || data.isBlank() || DONE_MARKER.equals(data.trim())) {
                return;
            }
            JsonNode root = parseChunk(data);
            if (root.has("error")) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "上游返回错误: " + root.path("error").toString());
            }
            JsonNode usageNode = root.path("usage");
            if (usageNode.isObject()) {
                usage[0] = ChatUsage.of(intOf(usageNode, "prompt_tokens"), intOf(usageNode, "completion_tokens"));
            }
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            String content = delta.path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                listener.onText(content);
            }
            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isArray()) {
                for (JsonNode toolCall : toolCalls) {
                    JsonNode indexNode = toolCall.path("index");
                    Integer index = indexNode.isNumber() ? indexNode.asInt() : null;
                    JsonNode function = toolCall.path("function");
                    accumulator.append(index,
                            toolCall.path("id").asText(null),
                            function.path("name").asText(null),
                            function.path("arguments").asText(null));
                }
            }
            String reason = choice.path("finish_reason").asText(null);
            if (reason != null && !reason.isBlank()) {
                finishReason[0] = reason;
            }
        });

        for (ChatToolCall call : accumulator.drain()) {
            listener.onToolCall(call);
        }
        if (usage[0] != null) {
            listener.onUsage(usage[0]);
        }
        listener.onFinish(finishReason[0] == null ? "stop" : finishReason[0]);
    }

    /** 请求体。 */
    private String buildBody(String modelName, ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("stream", true);
        body.put("messages", buildMessages(request));
        List<ChatTool> tools = request == null ? null : request.getTools();
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> definitions = new ArrayList<>(tools.size());
            for (ChatTool tool : tools) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", tool.name());
                function.put("description", tool.description() == null ? "" : tool.description());
                function.put("parameters", tool.schema() == null ? Map.of("type", "object") : tool.schema());
                definitions.add(Map.of("type", "function", "function", function));
            }
            body.put("tools", definitions);
        }
        body.putAll(ChatParamSupport.params(request));
        return json.toJson(body);
    }

    /** 消息映射：assistant 的工具调用与 tool 结果的形状和 OpenAI 一致。 */
    private List<Map<String, Object>> buildMessages(ChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage message : ChatParamSupport.allMessages(request)) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("role", message.getRole());
            mapped.put("content", message.getContent() == null ? "" : message.getContent());
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ChatToolCall call : message.getToolCalls()) {
                    calls.add(Map.of(
                            "id", call.id(),
                            "type", "function",
                            "function", Map.of("name", call.name(), "arguments",
                                    call.arguments() == null ? "{}" : call.arguments())));
                }
                mapped.put("tool_calls", calls);
            }
            if ("tool".equals(message.getRole())) {
                mapped.put("tool_call_id", message.getToolCallId() == null ? "" : message.getToolCallId());
            }
            messages.add(mapped);
        }
        return messages;
    }

    /** 解析一帧；不是合法 JSON 一律归为上游响应无效。 */
    private JsonNode parseChunk(String data) {
        try {
            JsonNode root = json.mapper().readTree(data);
            if (root == null || !root.isObject()) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "上游流式响应不是 JSON 对象");
            }
            return root;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "上游流式响应不是有效 JSON", exception);
        }
    }

    private static Integer intOf(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }
}
