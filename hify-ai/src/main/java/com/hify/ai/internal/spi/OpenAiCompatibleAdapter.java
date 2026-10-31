package com.hify.ai.internal.spi;

import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.http.LlmHttpClient;
import com.hify.shared.json.JsonUtils;
import org.springframework.stereotype.Component;

/**
 * OpenAI 协议族的具体实现。
 *
 * <p>协议逻辑全在 {@link OpenAiAdapter} 里，这里不重复任何代码 —— 存在的意义是
 * 明确「这一档服务的是所有 OpenAI 兼容端点」，而不是特指 OpenAI 官方：
 * 自托管的 vLLM、DeepSeek、Qwen 与 OpenAI 官方走的是同一份请求与响应结构。
 *
 * <p>将来出现协议相近但地址结构不同的变体（如 Azure OpenAI 的部署路径），
 * 从 {@link OpenAiAdapter} 继承并覆写路径与请求头即可，不必改现有实现。
 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient http, JsonUtils json, ProviderCredentialCipher cipher) {
        super(http, json, cipher);
    }
}
