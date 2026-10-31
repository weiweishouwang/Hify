package com.hify.ai.internal.spi;

import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Anthropic Messages 协议的目录实现：{@code GET {base}/models}，
 * 鉴权用 {@code x-api-key} 与 {@code anthropic-version} 两个请求头。
 */
@Component
public class AnthropicAdapter extends AbstractCatalogAdapter {

    public AnthropicAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }

    @Override
    public String type() {
        return ProviderConfigSupport.ANTHROPIC;
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

    /** Anthropic 必须有 Key 与 API 版本，两者缺一都算配置不完整。 */
    @Override
    protected Map<String, String> authHeaders(ModelProviderDO provider) {
        var auth = authConfig(provider);
        String key = decryptKey(auth.get(ProviderConfigSupport.API_KEY));
        if (key == null || !(auth.get(ProviderConfigSupport.ANTHROPIC_VERSION) instanceof String version)) {
            throw ProviderConfigSupport.invalid("Anthropic 鉴权配置不完整");
        }
        return Map.of("x-api-key", key, "anthropic-version", version);
    }
}
