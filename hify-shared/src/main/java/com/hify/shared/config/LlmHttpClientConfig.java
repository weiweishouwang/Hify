package com.hify.shared.config;

import com.hify.shared.http.LlmHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 上游 LLM HTTP 客户端的装配。
 *
 * <p>{@link LlmHttpClient} 本身不依赖 Spring，可以脱离容器单独 new 出来用；
 * 这里只负责把它注册成容器里的单例，让 {@code hify-ai} 之类的模块直接注入。
 */
@Configuration
public class LlmHttpClientConfig {

    /**
     * 上游 LLM 调用客户端。
     *
     * <p>超时用类里的默认值：连接 5 秒、普通读 60 秒、流式读 120 秒。
     *
     * @return LLM HTTP 客户端
     */
    @Bean
    public LlmHttpClient llmHttpClient() {
        return new LlmHttpClient();
    }
}
