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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 目录接口适配器的公共部分：请求、响应校验、凭据解密。 */
abstract class AbstractCatalogAdapter implements ProviderAdapter {

    protected final LlmHttpClient http;
    protected final JsonUtils json;
    protected final ProviderCredentialCipher cipher;

    protected AbstractCatalogAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        this.http = http;
        this.json = json;
        this.cipher = cipher;
    }

    /** 目录端点路径，拼接在协议根地址之后。 */
    protected abstract String catalogPath();

    /** 目录响应里模型数组的字段名。 */
    protected abstract String catalogField();

    /** 模型数组元素里标识的字段名。 */
    protected abstract String modelIdField();

    /** 鉴权请求头；不需要鉴权的协议返回空。 */
    protected Map<String, String> authHeaders(ModelProviderDO provider) {
        return Map.of();
    }

    @Override
    public void testConnection(ModelProviderDO provider) {
        fetchCatalog(provider);
    }

    @Override
    public List<String> listModels(ModelProviderDO provider) {
        List<String> identifiers = new ArrayList<>();
        for (JsonNode item : fetchCatalog(provider).path(catalogField())) {
            String identifier = item.path(modelIdField()).asText(null);
            if (identifier != null && !identifier.isBlank()) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /** 取一次目录响应并校验结构，失败一律归为上游响应无效。 */
    protected JsonNode fetchCatalog(ModelProviderDO provider) {
        String base = ProviderConfigSupport.baseUrl(provider.getBaseUrl());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.putAll(authHeaders(provider));
        String response = http.get(base + catalogPath(), headers);
        try {
            JsonNode root = response == null ? null : json.mapper().readTree(response);
            if (root == null || !root.isObject() || !root.path(catalogField()).isArray()) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "供应商目录响应格式无效");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "供应商目录响应不是有效 JSON", exception);
        }
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
}
