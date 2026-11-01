package com.hify.ai.internal.spi;

import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 协议的目录实现：{@code GET {base}/models}，{@code data[].id}。
 *
 * <p>这一档覆盖 OpenAI 官方与全部第三方 OpenAI 兼容端点 —— 路径、鉴权头和
 * 响应结构完全一致，彼此只差 base_url，所以不需要再拆子类。
 */
public class OpenAiAdapter extends AbstractCatalogAdapter {

    protected OpenAiAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.OPENAI;
    }

    @Override
    protected String catalogPath() {
        return "/models";
    }

    @Override
    protected String catalogField() {
        return "data";
    }

    @Override
    protected String modelIdField() {
        return "id";
    }

    /** 自托管兼容端点可以不鉴权；配了 Key 才发 Authorization。 */
    @Override
    protected Map<String, String> authHeaders(ModelProviderDO provider) {
        String key = decryptKey(authConfig(provider).get(ProviderConfigSupport.API_KEY));
        return key == null ? Map.of() : Map.of("Authorization", "Bearer " + key);
    }

    /**
     * OpenAI 形状的向量化：{@code POST {base}/embeddings}，
     * 请求体 {@code {"model": ..., "input": [...]}}，响应 {@code data[].embedding}。
     */
    @Override
    public List<float[]> embed(ModelProviderDO provider, String modelName, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String base = ProviderConfigSupport.baseUrl(provider.getBaseUrl());
        Map<String, String> headers = new LinkedHashMap<>(authHeaders(provider));
        headers.put("Content-Type", "application/json; charset=utf-8");
        String body = json.toJson(Map.of("model", modelName, "input", texts));
        String response = http.post(base + "/embeddings", headers, body);
        return parseEmbeddingArray(response, texts.size());
    }
}
