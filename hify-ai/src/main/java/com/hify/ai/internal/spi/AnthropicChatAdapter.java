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
 * Anthropic Messages 协议的对话实现：{@code POST {base}/messages}。
 *
 * <p><b>和 OpenAI 兼容档差得最多的地方</b>：
 * <ul>
 *   <li>系统提示词是顶层字段 {@code system}，不是一条消息</li>
 *   <li>{@code max_tokens} 必填</li>
 *   <li>工具定义用 {@code input_schema}，不是 {@code parameters}</li>
 *   <li>工具结果是 <b>user 消息</b>里的 {@code tool_result} 块，不是 tool 消息</li>
 *   <li>助手要调工具时，消息体是 {@code tool_use} 块，不是 {@code tool_calls} 数组</li>
 * </ul>
 * 这些差异全在这里收敛 —— 编排层只认统一的 {@link ChatListener}。
 */
@Component
public class AnthropicChatAdapter extends AbstractProviderAdapter implements ChatProtocolAdapter {

    public AnthropicChatAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.ANTHROPIC;
    }

    @Override
    public void stream(ModelProviderDO provider, String modelName, ChatRequest request, ChatListener listener) {
        String url = baseUrl(provider) + "/messages";
        Map<String, Object> auth = authConfig(provider);
        String key = decryptKey(auth.get(ProviderConfigSupport.API_KEY));
        if (key == null || !(auth.get(ProviderConfigSupport.ANTHROPIC_VERSION) instanceof String version)) {
            throw ProviderConfigSupport.invalid("Anthropic 鉴权配置不完整");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Accept", "text/event-stream");
        headers.put("x-api-key", key);
        headers.put("anthropic-version", version);

        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        String[] finishReason = {null};
        int[] inputTokens = {0};
        int[] outputTokens = {0};

        http.stream(url, headers, buildBody(modelName, request), (eventType, data) -> {
            if (data == null || data.isBlank()) {
                return;
            }
            JsonNode root = parseChunk(data);
            String type = root.path("type").asText(eventType == null ? "" : eventType);
            switch (type) {
                case "message_start" -> {
                    JsonNode usage = root.path("message").path("usage");
                    inputTokens[0] = usage.path("input_tokens").asInt(0);
                    outputTokens[0] = usage.path("output_tokens").asInt(0);
                }
                case "content_block_start" -> {
                    JsonNode block = root.path("content_block");
                    if ("tool_use".equals(block.path("type").asText(null))) {
                        int index = root.path("index").asInt(0);
                        accumulator.append(index,
                                block.path("id").asText(null),
                                block.path("name").asText(null),
                                null);
                    }
                }
                case "content_block_delta" -> {
                    JsonNode delta = root.path("delta");
                    String deltaType = delta.path("type").asText("");
                    if ("text_delta".equals(deltaType)) {
                        String text = delta.path("text").asText(null);
                        if (text != null && !text.isEmpty()) {
                            listener.onText(text);
                        }
                    } else if ("input_json_delta".equals(deltaType)) {
                        int index = root.path("index").asInt(0);
                        accumulator.append(index, null, null, delta.path("partial_json").asText(null));
                    }
                }
                case "message_delta" -> {
                    String stopReason = root.path("delta").path("stop_reason").asText(null);
                    if (stopReason != null && !stopReason.isBlank()) {
                        finishReason[0] = normalizeFinishReason(stopReason);
                    }
                    JsonNode usage = root.path("usage");
                    if (usage.path("output_tokens").isNumber()) {
                        outputTokens[0] = usage.path("output_tokens").asInt();
                    }
                    if (usage.path("input_tokens").isNumber()) {
                        inputTokens[0] = usage.path("input_tokens").asInt();
                    }
                }
                default -> {
                    // content_block_stop / message_stop / ping：不需要额外处理
                }
            }
        });

        for (ChatToolCall call : accumulator.drain()) {
            listener.onToolCall(call);
        }
        ChatUsage usage = ChatUsage.of(inputTokens[0], outputTokens[0]);
        if (usage != null) {
            listener.onUsage(usage);
        }
        listener.onFinish(finishReason[0] == null ? "stop" : finishReason[0]);
    }

    /** Anthropic 的结束原因映射到通用语义。 */
    private static String normalizeFinishReason(String stopReason) {
        return switch (stopReason) {
            case "tool_use" -> "tool_calls";
            case "max_tokens" -> "length";
            default -> "stop";
        };
    }

    private String buildBody(String modelName, ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("stream", true);
        body.put("max_tokens", ChatParamSupport.maxTokensOrDefault(request));
        String system = ChatParamSupport.systemPrompt(request);
        if (system != null && !system.isBlank()) {
            body.put("system", system);
        }
        body.put("messages", buildMessages(request));
        List<ChatTool> tools = request == null ? null : request.getTools();
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> definitions = new ArrayList<>(tools.size());
            for (ChatTool tool : tools) {
                Map<String, Object> definition = new LinkedHashMap<>();
                definition.put("name", tool.name());
                definition.put("description", tool.description() == null ? "" : tool.description());
                definition.put("input_schema", tool.schema() == null ? Map.of("type", "object") : tool.schema());
                definitions.add(definition);
            }
            body.put("tools", definitions);
        }
        Double temperature = ChatParamSupport.temperature(request);
        Double topP = ChatParamSupport.topP(request);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (topP != null) {
            body.put("top_p", topP);
        }
        return json.toJson(body);
    }

    /**
     * 消息映射。
     *
     * <p>工具结果要变成 <b>user 消息</b>里的 {@code tool_result} 块，而且必须紧跟在
     * 发起调用的 assistant 消息之后 —— 连续多个工具结果合并进同一条 user 消息。
     */
    private List<Map<String, Object>> buildMessages(ChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> pendingResults = new ArrayList<>();

        for (ChatMessage message : ChatParamSupport.conversation(request)) {
            if ("tool".equals(message.getRole())) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", message.getToolCallId() == null ? "" : message.getToolCallId());
                block.put("content", message.getContent() == null ? "" : message.getContent());
                pendingResults.add(block);
                continue;
            }
            flushResults(messages, pendingResults);
            if ("assistant".equals(message.getRole()) && message.getToolCalls() != null
                    && !message.getToolCalls().isEmpty()) {
                List<Map<String, Object>> blocks = new ArrayList<>();
                for (ChatToolCall call : message.getToolCalls()) {
                    Map<String, Object> block = new LinkedHashMap<>();
                    block.put("type", "tool_use");
                    block.put("id", call.id());
                    block.put("name", call.name());
                    block.put("input", parseArguments(call.arguments()));
                    blocks.add(block);
                }
                messages.add(Map.of("role", "assistant", "content", blocks));
            } else {
                messages.add(Map.of("role", message.getRole(),
                        "content", message.getContent() == null ? "" : message.getContent()));
            }
        }
        flushResults(messages, pendingResults);
        return messages;
    }

    private static void flushResults(List<Map<String, Object>> messages, List<Map<String, Object>> pending) {
        if (pending.isEmpty()) {
            return;
        }
        messages.add(Map.of("role", "user", "content", List.copyOf(pending)));
        pending.clear();
    }

    /** 工具入参是 JSON 字符串，Anthropic 要的是对象。 */
    private Object parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = json.mapper().readTree(arguments);
            return node == null || !node.isObject() ? Map.of() : node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "工具入参不是合法 JSON", exception);
        }
    }

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
}
