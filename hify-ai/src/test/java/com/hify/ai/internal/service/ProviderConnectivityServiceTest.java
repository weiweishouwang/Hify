package com.hify.ai.internal.service;

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

/** 使用本地 HTTP 服务器验证三种协议，不调用任何真实供应商。 */
class ProviderConnectivityServiceTest {
    MockWebServer server;
    ProviderCredentialCipher cipher;
    ProviderConnectivityService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new ProviderConnectivityService(new LlmHttpClient(), new JsonUtils(new ObjectMapper()), cipher);
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
    void openAiUsesDecryptedBearerAndCatalogEndpoint() throws Exception {
        var provider = provider("openai");
        provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("test-secret")));
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        service.testConnection(provider);
        var request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/v1/models");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-secret");
        assertThat(request.getBodySize()).isZero();
    }

    @Test
    void anthropicUsesKeyAndVersionHeaders() throws Exception {
        var provider = provider("anthropic");
        provider.setAuthConfig(Map.of("apiKey", cipher.encrypt("ant-test"), "anthropicVersion", "2023-06-01"));
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        service.testConnection(provider);
        var request = server.takeRequest();
        assertThat(request.getHeader("x-api-key")).isEqualTo("ant-test");
        assertThat(request.getHeader("anthropic-version")).isEqualTo("2023-06-01");
        assertThat(request.getHeader("Authorization")).isNull();
    }

    @Test
    void ollamaUsesTagsWithoutAuthenticationOrInference() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"models\":[]}"));
        service.testConnection(provider("ollama"));
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/tags");
        assertThat(request.getHeader("Authorization")).isNull();
        assertThat(request.getHeader("x-api-key")).isNull();
    }

    @Test
    void successStatusWithInvalidPayloadIsNotHealthy() {
        for (String payload : new String[]{"<html>login</html>", "{}", "{\"data\":{}}", "null"}) {
            server.enqueue(new MockResponse().setBody(payload));
            assertThatThrownBy(() -> service.testConnection(provider("openai"))).isInstanceOf(LlmApiException.class);
        }
    }
}
