package com.hify.ai.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 只读目录接口探测端点与鉴权，不验证具体模型推理能力，也不调用业务熔断器。 */
@Service
public class ProviderConnectivityService {
    private final LlmHttpClient http;
    private final JsonUtils json;
    private final ProviderCredentialCipher cipher;

    public ProviderConnectivityService(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        this.http = http;
        this.json = json;
        this.cipher = cipher;
    }

    public void testConnection(ModelProviderDO provider) {
        String type = ProviderConfigSupport.type(provider.getType());
        String base = ProviderConfigSupport.baseUrl(provider.getBaseUrl());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        boolean ollama = ProviderConfigSupport.OLLAMA.equals(type);
        if (!ollama) {
            var auth = provider.getAuthConfig() == null ? Map.<String, Object>of() : provider.getAuthConfig();
            String key = decryptKey(auth.get(ProviderConfigSupport.API_KEY));
            if (ProviderConfigSupport.ANTHROPIC.equals(type)) {
                if (key == null || !(auth.get(ProviderConfigSupport.ANTHROPIC_VERSION) instanceof String version)) {
                    throw ProviderConfigSupport.invalid("Anthropic 鉴权配置不完整");
                }
                headers.put("x-api-key", key);
                headers.put("anthropic-version", version);
            } else if (key != null) {
                headers.put("Authorization", "Bearer " + key);
            }
        }
        String response = http.get(base + (ollama ? "/api/tags" : "/models"), headers);
        try {
            JsonNode root = response == null ? null : json.mapper().readTree(response);
            if (root == null || !root.isObject() || !root.path(ollama ? "models" : "data").isArray()) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "供应商目录响应格式无效");
            }
        } catch (JsonProcessingException exception) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "供应商目录响应不是有效 JSON", exception);
        }
    }

    private String decryptKey(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> envelope)) {
            throw ProviderConfigSupport.invalid("接入点凭据格式无效");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (var entry : envelope.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw ProviderConfigSupport.invalid("接入点凭据格式无效");
            }
            typed.put(key, entry.getValue());
        }
        return cipher.decrypt(typed);
    }
}
