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
abstract class AbstractCatalogAdapter extends AbstractProviderAdapter implements ProviderAdapter {

    protected AbstractCatalogAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
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

    /**
     * 默认不支持 embedding。
     *
     * <p>Anthropic 没有 embedding 接口 —— 这是协议事实，不是配置错误。
     * 支持 embedding 的协议（openai / ollama）各自覆写。
     */
    @Override
    public List<float[]> embed(ModelProviderDO provider, String modelName, List<String> texts) {
        throw ProviderConfigSupport.invalid(type() + " 协议不支持 embedding");
    }

    /** 解析 OpenAI 形状的 embedding 响应：{@code data[].embedding}。 */
    protected List<float[]> parseEmbeddingArray(String response, int expected) {
        JsonNode root = readJson(response, "embedding 响应不是有效 JSON");
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "embedding 响应格式无效");
        }
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "embedding 响应缺少向量");
            }
            float[] vector = new float[embedding.size()];
            for (int index = 0; index < embedding.size(); index++) {
                vector[index] = (float) embedding.get(index).asDouble();
            }
            vectors.add(vector);
        }
        if (vectors.size() != expected) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR,
                    "embedding 响应条数与请求不一致: 期望 " + expected + "，实际 " + vectors.size());
        }
        return vectors;
    }

    /** 解析单个向量：{@code embedding} 数组。 */
    protected float[] parseSingleEmbedding(String response) {
        JsonNode root = readJson(response, "embedding 响应不是有效 JSON");
        JsonNode embedding = root.path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "embedding 响应缺少向量");
        }
        float[] vector = new float[embedding.size()];
        for (int index = 0; index < embedding.size(); index++) {
            vector[index] = (float) embedding.get(index).asDouble();
        }
        return vector;
    }

    /** 取一次目录响应并校验结构，失败一律归为上游响应无效。 */
    protected JsonNode fetchCatalog(ModelProviderDO provider) {
        String base = baseUrl(provider);
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
}
