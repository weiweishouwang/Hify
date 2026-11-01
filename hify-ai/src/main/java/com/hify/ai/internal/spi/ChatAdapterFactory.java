package com.hify.ai.internal.spi;

import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按协议档位取对话适配器，取代散落在服务层的 type 分发。
 *
 * <p>与 {@link ProviderAdapterFactory}（目录适配）并列，两者是同一套策略模式的两个面。
 */
@Component
public class ChatAdapterFactory {

    private final Map<String, ChatProtocolAdapter> adapters = new LinkedHashMap<>();

    public ChatAdapterFactory(OpenAiChatAdapter openAi, AnthropicChatAdapter anthropic, OllamaChatAdapter ollama) {
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
    public ChatProtocolAdapter getAdapter(String type) {
        ChatProtocolAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的接入点协议: " + type);
        }
        return adapter;
    }
}
