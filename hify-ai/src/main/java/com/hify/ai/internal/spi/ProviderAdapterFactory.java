package com.hify.ai.internal.spi;

import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按协议档位取适配器，取代散落在服务层的 type 分支。
 *
 * <p>新增一种协议时只改这里：加一个适配器实现并登记，调用方不用动。
 */
@Component
public class ProviderAdapterFactory {

    private final Map<String, ProviderAdapter> adapters = new LinkedHashMap<>();

    public ProviderAdapterFactory(OpenAiCompatibleAdapter openAi, AnthropicAdapter anthropic, OllamaAdapter ollama) {
        adapters.put(ProviderConfigSupport.OPENAI, openAi);
        adapters.put(ProviderConfigSupport.ANTHROPIC, anthropic);
        adapters.put(ProviderConfigSupport.OLLAMA, ollama);
    }

    /**
     * 取协议档位对应的适配器。
     *
     * @param type 协议档位
     * @return 适配器实例
     * @throws BizException 协议不受支持时
     */
    public ProviderAdapter getAdapter(String type) {
        ProviderAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的接入点协议: " + type);
        }
        return adapter;
    }
}
