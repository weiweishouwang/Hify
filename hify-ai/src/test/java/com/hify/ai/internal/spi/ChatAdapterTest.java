package com.hify.ai.internal.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatTool;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 三套协议的对话请求体形状与流式响应解析；只连本地 HTTP 服务器，不调用真实供应商。 */
class ChatAdapterTest {

    private MockWebServer server;
    private ProviderCredentialCipher cipher;
    private ChatAdapterFactory factory;

    /** 收集回调的测试替身。 */
    static class RecordingListener implements ChatListener {
        final StringBuilder text = new StringBuilder();
        final List<ChatToolCall> toolCalls = new ArrayList<>();
        String finishReason;
        Integer promptTokens;
        Integer completionTokens;

        @Override
        public void onText(String delta) {
            text.append(delta);
        }

        @Override
        public void onToolCall(ChatToolCall call) {
            toolCalls.add(call);
        }

        @Override
        public void onUsage(com.hify.ai.api.ChatUsage usage) {
            promptTokens = usage.promptTokens();
            completionTokens = usage.completionTokens();
        }

        @Override
        public void onFinish(String finishReason) {
            this.finishReason = finishReason;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        factory = new ChatAdapterFactory(
                new OpenAiChatAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new AnthropicChatAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new OllamaChatAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher));
    }

    @AfterEach
    void close() throws Exception {
        server.shutdown();
    }

    ModelProviderDO provider(String type) {
        var provider = new ModelProviderDO();
        provider.setType(type);
        provider.setBaseUrl(server.url(type.equals("ollama") ? "/" : "/v1/").toString());
        if ("anthropic".equals(type)) {
            provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("k"), "anthropicVersion", "2023-06-01"));
        }
        return provider;
    }

    ChatRequest request() {
        var request = new ChatRequest();
        request.setMessages(new ArrayList<>(List.of(
                ChatMessage.system("你是客服"),
                ChatMessage.user("订单在哪"),
                ChatMessage.assistant("查一下"))));
        request.setTools(List.of(new ChatTool("查订单", "按单号查订单",
                Map.of("type", "object", "properties", Map.of("no", Map.of("type", "string"))))));
        request.setParams(Map.of("temperature", 0.3, "max_tokens", 512));
        return request;
    }

    static MockResponse sse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    @Test
    @DisplayName("OpenAI 兼容：请求体形状与工具定义")
    void openAiRequestShape() throws Exception {
        server.enqueue(sse("data: [DONE]\n\n"));
        factory.getAdapter("openai").stream(provider("openai"), "qwen-plus", request(), new RecordingListener());

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");
        var body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("qwen-plus");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.3);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.path("messages")).hasSize(3);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(body.path("tools").get(0).path("type").asText()).isEqualTo("function");
        assertThat(body.path("tools").get(0).path("function").path("name").asText()).isEqualTo("查订单");
        assertThat(body.path("tools").get(0).path("function").path("parameters").path("type").asText())
                .isEqualTo("object");
    }

    @Test
    @DisplayName("OpenAI 兼容：文本增量与分片到达的工具参数")
    void openAiParsesStreamAndAssemblesToolArguments() {
        server.enqueue(sse("""
                data: {"choices":[{"delta":{"content":"你好"}}]}

                data: {"choices":[{"delta":{"content":"，在找"}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"查订单","arguments":"{\\"no\\":"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"A1\\"}"}}]}}]}

                data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":11,"completion_tokens":7}}

                data: [DONE]

                """));
        var listener = new RecordingListener();
        factory.getAdapter("openai").stream(provider("openai"), "qwen-plus", request(), listener);

        assertThat(listener.text.toString()).isEqualTo("你好，在找");
        assertThat(listener.toolCalls).hasSize(1);
        assertThat(listener.toolCalls.getFirst().id()).isEqualTo("call_1");
        assertThat(listener.toolCalls.getFirst().name()).isEqualTo("查订单");
        assertThat(listener.toolCalls.getFirst().arguments()).isEqualTo("{\"no\":\"A1\"}");
        assertThat(listener.finishReason).isEqualTo("tool_calls");
        assertThat(listener.promptTokens).isEqualTo(11);
        assertThat(listener.completionTokens).isEqualTo(7);
    }

    @Test
    @DisplayName("Anthropic：系统提示词独立成字段，工具结果进 user 消息")
    void anthropicRequestShape() throws Exception {
        server.enqueue(sse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"));
        var request = request();
        var assistant = ChatMessage.assistant("");
        assistant.setToolCalls(List.of(new ChatToolCall("toolu_1", "查订单", "{\"no\":\"A1\"}")));
        request.getMessages().add(assistant);
        request.getMessages().add(ChatMessage.tool("toolu_1", "查订单", "订单在配送中"));
        factory.getAdapter("anthropic").stream(provider("anthropic"), "claude-x", request, new RecordingListener());

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/v1/messages");
        assertThat(recorded.getHeader("x-api-key")).isEqualTo("k");
        assertThat(recorded.getHeader("anthropic-version")).isEqualTo("2023-06-01");
        var body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertThat(body.path("system").asText()).isEqualTo("你是客服");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(512);
        assertThat(body.path("tools").get(0).path("name").asText()).isEqualTo("查订单");
        assertThat(body.path("tools").get(0).path("input_schema").path("type").asText()).isEqualTo("object");

        var messages = body.path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(2).path("content").get(0).path("type").asText()).isEqualTo("tool_use");
        assertThat(messages.get(2).path("content").get(0).path("id").asText()).isEqualTo("toolu_1");
        assertThat(messages.get(3).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(3).path("content").get(0).path("type").asText()).isEqualTo("tool_result");
        assertThat(messages.get(3).path("content").get(0).path("tool_use_id").asText()).isEqualTo("toolu_1");
    }

    @Test
    @DisplayName("Anthropic：具名事件解析文本与工具参数")
    void anthropicParsesEvents() {
        server.enqueue(sse("""
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":9,"output_tokens":0}}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"在查"}}

                event: content_block_start
                data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_9","name":"查订单"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\\"no\\":\\"A1\\"}"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":13}}

                """));
        var listener = new RecordingListener();
        factory.getAdapter("anthropic").stream(provider("anthropic"), "claude-x", request(), listener);

        assertThat(listener.text.toString()).isEqualTo("在查");
        assertThat(listener.toolCalls).hasSize(1);
        assertThat(listener.toolCalls.getFirst().id()).isEqualTo("toolu_9");
        assertThat(listener.toolCalls.getFirst().arguments()).isEqualTo("{\"no\":\"A1\"}");
        assertThat(listener.finishReason).isEqualTo("tool_calls");
        assertThat(listener.promptTokens).isEqualTo(9);
        assertThat(listener.completionTokens).isEqualTo(13);
    }

    @Test
    @DisplayName("Ollama 原生：按行切 NDJSON，工具参数是对象")
    void ollamaParsesNdjson() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/x-ndjson")
                .setBody("""
                        {"message":{"role":"assistant","content":"在查"},"done":false}
                        {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"查订单","arguments":{"no":"A1"}}}]},"done":false}
                        {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":12,"eval_count":5}
                        """));
        var listener = new RecordingListener();
        factory.getAdapter("ollama").stream(provider("ollama"), "qwen2.5:7b", request(), listener);

        assertThat(listener.text.toString()).isEqualTo("在查");
        assertThat(listener.toolCalls).hasSize(1);
        assertThat(listener.toolCalls.getFirst().name()).isEqualTo("查订单");
        assertThat(listener.toolCalls.getFirst().arguments()).isEqualTo("{\"no\":\"A1\"}");
        assertThat(listener.finishReason).isEqualTo("stop");
        assertThat(listener.promptTokens).isEqualTo(12);
        assertThat(listener.completionTokens).isEqualTo(5);

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/api/chat");
        var body = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertThat(body.path("options").path("num_predict").asInt()).isEqualTo(512);
        assertThat(body.path("options").path("temperature").asDouble()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("工厂按协议档位取适配器")
    void factoryResolvesByType() {
        assertThat(factory.getAdapter("openai")).isInstanceOf(OpenAiChatAdapter.class);
        assertThat(factory.getAdapter("anthropic")).isInstanceOf(AnthropicChatAdapter.class);
        assertThat(factory.getAdapter("ollama")).isInstanceOf(OllamaChatAdapter.class);
        assertThatThrownBy(() -> factory.getAdapter("gemini"))
                .isInstanceOf(com.hify.shared.exception.BizException.class);
    }
}
