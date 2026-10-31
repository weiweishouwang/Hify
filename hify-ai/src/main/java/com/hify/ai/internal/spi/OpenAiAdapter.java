package com.hify.ai.internal.spi;

import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;

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
}
