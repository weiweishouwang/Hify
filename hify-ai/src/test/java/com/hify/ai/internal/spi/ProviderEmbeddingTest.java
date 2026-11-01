package com.hify.ai.internal.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 各协议的向量化：请求路径、请求体、响应解析与不支持时的拒绝。 */
class ProviderEmbeddingTest {

    MockWebServer server;
    ProviderCredentialCipher cipher;
    ProviderAdapterFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        factory = new ProviderAdapterFactory(
                new OpenAiCompatibleAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new AnthropicAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new OllamaAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher));
    }

    @AfterEach
    void close() throws Exception {
        server.shutdown();
    }

    ModelProviderDO provider(String type) {
        var provider = new ModelProviderDO();
        provider.setType(type);
        provider.setBaseUrl(server.url(type.equals("ollama") ? "/" : "/v1/").toString());
        return provider;
    }

    @Test
    @DisplayName("OpenAI 档一次发一批，路径是 {base}/embeddings")
    void openAiEmbedsBatch() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}"));

        var vectors = factory.getAdapter("openai")
                .embed(provider("openai"), "text-embedding-3-small", List.of("甲", "乙"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        assertThat(vectors.get(1)).containsExactly(0.3f, 0.4f);

        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/embeddings");
        assertThat(request.getBody().readUtf8())
                .contains("text-embedding-3-small")
                .contains("甲")
                .contains("乙");
    }

    @Test
    @DisplayName("Ollama 档原生接口一次一条，路径是 {base}/api/embeddings")
    void ollamaEmbedsOneByOne() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"embedding\":[0.5,0.6]}"));
        server.enqueue(new MockResponse().setBody("{\"embedding\":[0.7,0.8]}"));

        var vectors = factory.getAdapter("ollama")
                .embed(provider("ollama"), "nomic-embed-text", List.of("甲", "乙"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.5f, 0.6f);

        var first = server.takeRequest();
        assertThat(first.getPath()).isEqualTo("/api/embeddings");
        assertThat(first.getBody().readUtf8()).contains("nomic-embed-text").contains("甲");
        var second = server.takeRequest();
        assertThat(second.getBody().readUtf8()).contains("乙");
    }

    @Test
    @DisplayName("Anthropic 档不提供 embedding，直接拒绝")
    void anthropicRejectsEmbedding() {
        assertThatThrownBy(() -> factory.getAdapter("anthropic")
                .embed(provider("anthropic"), "claude", List.of("甲")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持 embedding");
    }

    @Test
    @DisplayName("响应条数与请求不一致时判为上游响应无效")
    void rejectsMismatchedCount() {
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"embedding\":[0.1,0.2]}]}"));

        assertThatThrownBy(() -> factory.getAdapter("openai")
                .embed(provider("openai"), "m", List.of("甲", "乙")))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("条数");
    }

    @Test
    @DisplayName("响应里没有向量时判为上游响应无效")
    void rejectsMissingVector() {
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"foo\":1}]}"));

        assertThatThrownBy(() -> factory.getAdapter("openai")
                .embed(provider("openai"), "m", List.of("甲")))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("缺少向量");
    }

    @Test
    @DisplayName("空文本不发起请求")
    void emptyTextsSkipRequest() {
        assertThat(factory.getAdapter("openai").embed(provider("openai"), "m", List.of())).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("带密钥时向量化请求带上鉴权头")
    void sendsAuthorizationHeader() throws Exception {
        var provider = provider("openai");
        provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("test-secret")));
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"embedding\":[0.1]}]}"));

        factory.getAdapter("openai").embed(provider, "m", List.of("甲"));

        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer test-secret");
    }
}
