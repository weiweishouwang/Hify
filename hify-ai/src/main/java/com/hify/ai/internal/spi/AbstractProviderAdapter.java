package com.hify.ai.internal.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接入点适配器的公共部分：HTTP 客户端、JSON 工具、凭据解密。
 *
 * <p>目录适配（{@link AbstractCatalogAdapter}）与对话适配（{@link AbstractChatAdapter}）
 * 共用这些 —— 凭据怎么存、怎么解、响应怎么读，跟「这个适配器干什么」无关。
 */
abstract class AbstractProviderAdapter {

    protected final LlmHttpClient http;
    protected final JsonUtils json;
    protected final ProviderCredentialCipher cipher;

    protected AbstractProviderAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        this.http = http;
        this.json = json;
        this.cipher = cipher;
    }

    /** 协议根地址（已去掉末尾斜杠）。 */
    protected String baseUrl(ModelProviderDO provider) {
        return ProviderConfigSupport.baseUrl(provider.getBaseUrl());
    }

    /** 解密已保存的 API Key；未配置时返回 null。 */
    protected String decryptKey(Object value) {
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

    /** 接入点已保存的鉴权配置。 */
    protected Map<String, Object> authConfig(ModelProviderDO provider) {
        return provider.getAuthConfig() == null ? Map.of() : provider.getAuthConfig();
    }

    /** 解析响应体为 JSON 对象；结构不对一律归为上游响应无效。 */
    protected JsonNode readJson(String response, String message) {
        try {
            JsonNode root = response == null ? null : json.mapper().readTree(response);
            if (root == null || !root.isObject()) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, message);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, message, exception);
        }
    }
}
