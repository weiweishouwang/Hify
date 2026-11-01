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
 * Ollama 原生协议的对话实现：{@code POST {base}/api/chat}。
 *
 * <p><b>两处与 OpenAI 兼容档不同</b>：
 * <ul>
 *   <li><b>流是 NDJSON 不是 SSE</b> —— 一行一个完整 JSON 对象，所以走
 *       {@link com.hify.shared.http.LlmHttpClient#streamLines}</li>
 *   <li><b>工具参数是对象不是字符串</b> —— {@code function.arguments} 直接是 JSON 对象，
 *       这里转成字符串再交给上层</li>
 * </ul>
 *
 * <p>不蹭 OpenAI 兼容层：兼容层会忽略 {@code options.num_ctx}（模型按 4096 上下文加载，
 * 开始胡言乱语），而且原生档的 tool 行为更可靠（CLAUDE.md §3.5）。
 */
@Component
public class OllamaChatAdapter extends AbstractProviderAdapter implements ChatProtocolAdapter {

    public OllamaChatAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.OLLAMA;
    }

    @Override
    public void stream(ModelProviderDO provider, String modelName, ChatRequest request, ChatListener listener) {
        String url = baseUrl(provider) + "/api/chat";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");

        ToolCallAccumulator accumulator = new ToolCallAccumulator();
        String[] finishReason = {null};
        int[] promptTokens = {0};
        int[] evalTokens = {0};

        http.streamLines(url, headers, buildBody(modelName, request), (eventType, data) -> {
            if (data == null || data.isBlank()) {
                return;
            }
            JsonNode root = parseLine(data);
            JsonNode message = root.path("message");
            String content = message.path("content").asText(null);
            if (content != null && !content.isEmpty()) {
                listener.onText(content);
            }
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray()) {
                int index = 0;
                for (JsonNode toolCall : toolCalls) {
                    JsonNode function = toolCall.path("function");
                    accumulator.appendWhole(index, null,
                            function.path("name").asText(null),
                            json.toJson(function.path("arguments")));
                    index++;
                }
            }
            if (root.path("done").asBoolean(false)) {
                String reason = root.path("done_reason").asText(null);
                finishReason[0] = reason == null || reason.isBlank() ? "stop" : reason;
                promptTokens[0] = root.path("prompt_eval_count").asInt(0);
                evalTokens[0] = root.path("eval_count").asInt(0);
            }
        });

        for (ChatToolCall call : accumulator.drain()) {
            listener.onToolCall(call);
        }
        ChatUsage usage = ChatUsage.of(promptTokens[0], evalTokens[0]);
        if (usage != null) {
            listener.onUsage(usage);
        }
        listener.onFinish(finishReason[0] == null ? "stop" : finishReason[0]);
    }

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
        Map<String, Object> options = new LinkedHashMap<>();
        Double temperature = ChatParamSupport.temperature(request);
        Double topP = ChatParamSupport.topP(request);
        Integer maxTokens = ChatParamSupport.maxTokens(request);
        if (temperature != null) {
            options.put("temperature", temperature);
        }
        if (topP != null) {
            options.put("top_p", topP);
        }
        if (maxTokens != null) {
            options.put("num_predict", maxTokens);
        }
        if (!options.isEmpty()) {
            body.put("options", options);
        }
        return json.toJson(body);
    }

    private List<Map<String, Object>> buildMessages(ChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage message : ChatParamSupport.allMessages(request)) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("role", message.getRole());
            mapped.put("content", message.getContent() == null ? "" : message.getContent());
            if ("tool".equals(message.getRole())) {
                mapped.put("tool_name", message.getName() == null ? "" : message.getName());
            }
            messages.add(mapped);
        }
        return messages;
    }

    private JsonNode parseLine(String data) {
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
