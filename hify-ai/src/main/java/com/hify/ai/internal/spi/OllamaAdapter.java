package com.hify.ai.internal.spi;

import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 原生协议的目录实现：{@code GET {base}/api/tags}，无需鉴权，
 * 响应是 {@code models[].name}。
 */
@Component
public class OllamaAdapter extends AbstractCatalogAdapter {

    public OllamaAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.OLLAMA;
    }

    @Override
    protected String catalogPath() {
        return "/api/tags";
    }

    @Override
    protected String catalogField() {
        return "models";
    }

    @Override
    protected String modelIdField() {
        return "name";
    }

    /**
     * Ollama 原生的向量化：{@code POST {base}/api/embeddings}，
     * 请求体 {@code {"model": ..., "prompt": "..."}}，响应 {@code embedding}。
     *
     * <p>原生接口一次只吃一条，所以这里逐条请求。
     */
    @Override
    public List<float[]> embed(ModelProviderDO provider, String modelName, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        String base = ProviderConfigSupport.baseUrl(provider.getBaseUrl());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        List<float[]> vectors = new ArrayList<>();
        for (String text : texts) {
            String body = json.toJson(Map.of("model", modelName, "prompt", text));
            String response = http.post(base + "/api/embeddings", headers, body);
            vectors.add(parseSingleEmbedding(response));
        }
        return vectors;
    }
}
