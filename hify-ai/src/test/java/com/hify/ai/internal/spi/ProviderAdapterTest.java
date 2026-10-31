package com.hify.ai.internal.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/** 三个协议适配器的路径、鉴权头与模型列举；只连本地 HTTP 服务器，不调用真实供应商。 */
class ProviderAdapterTest {
    MockWebServer server;
    ProviderCredentialCipher cipher;
    ProviderAdapterFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        factory = new ProviderAdapterFactory(new OpenAiCompatibleAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new AnthropicAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher),
                new OllamaAdapter(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher));
    }

    @AfterEach
    void close() throws Exception { server.shutdown(); }

    ModelProviderDO provider(String type) {
        var provider = new ModelProviderDO();
        provider.setType(type);
        provider.setBaseUrl(server.url(type.equals("ollama") ? "/" : "/v1/").toString());
        return provider;
    }

    @Test
    void factoryResolvesAdapterByType() {
        assertThat(factory.getAdapter("openai")).isInstanceOf(OpenAiAdapter.class);
        assertThat(factory.getAdapter("anthropic")).isInstanceOf(AnthropicAdapter.class);
        assertThat(factory.getAdapter("ollama")).isInstanceOf(OllamaAdapter.class);
        assertThatThrownBy(() -> factory.getAdapter("gemini")).isInstanceOf(com.hify.shared.exception.BizException.class);
        assertThatThrownBy(() -> factory.getAdapter(null)).isInstanceOf(com.hify.shared.exception.BizException.class);
    }

    @Test
    void openAiUsesDecryptedBearerAndCatalogEndpoint() throws Exception {
        var provider = provider("openai");
        provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("test-secret")));
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"id\":\"gpt-x\"}]}"));
        factory.getAdapter("openai").testConnection(provider);
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/v1/models");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-secret");
        assertThat(request.getBodySize()).isZero();
    }

    @Test
    void openAiCompatibleEndpointMayOmitAuthentication() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        factory.getAdapter("openai").testConnection(provider("openai"));
        assertThat(server.takeRequest().getHeader("Authorization")).isNull();
    }

    @Test
    void anthropicUsesKeyAndVersionHeaders() throws Exception {
        var provider = provider("anthropic");
        provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("ant-test"), "anthropicVersion", "2023-06-01"));
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        factory.getAdapter("anthropic").testConnection(provider);
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/models");
        assertThat(request.getHeader("x-api-key")).isEqualTo("ant-test");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(request.getHeader("Authorization")).isNull();
    }

    @Test
    void anthropicWithoutCompleteCredentialsIsRejected() {
        var provider = provider("anthropic");
        provider.setAuthConfig(Map.of());
        assertThatThrownBy(() -> factory.getAdapter("anthropic").testConnection(provider))
                .isInstanceOf(com.hify.shared.exception.BizException.class);
    }

    @Test
    void ollamaUsesTagsWithoutAuthenticationOrInference() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"models\":[]}"));
        factory.getAdapter("ollama").testConnection(provider("ollama"));
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/tags");
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(request.getHeader("x-api-key")).isNull();
    }

    @Test
    void listModelsReturnsUpstreamIdentifiersPerProtocol() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"data\":[{\"id\":\"gpt-a\"},{\"id\":\"gpt-b\"}]}"));
        assertThat(factory.getAdapter("openai").listModels(provider("openai"))).containsExactly("gpt-a", "gpt-b");
        server.enqueue(new MockResponse().setBody("{\"models\":[{\"name\":\"llama3:8b\"}]}"));
        assertThat(factory.getAdapter("ollama").listModels(provider("ollama"))).containsExactly("llama3:8b");
    }

    @Test
    void successStatusWithInvalidPayloadIsNotHealthy() {
        for (String payload : new String[]{"<html>login</html>", "{}", "{\"data\":{}}", "null"}) {
            server.enqueue(new MockResponse().setBody(payload));
            assertThatThrownBy(() -> factory.getAdapter("openai").testConnection(provider("openai")))
                    .isInstanceOf(LlmApiException.class);
        }
    }
}
