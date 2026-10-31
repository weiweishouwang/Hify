package com.hify.ai.internal.spi;

import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

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
}
