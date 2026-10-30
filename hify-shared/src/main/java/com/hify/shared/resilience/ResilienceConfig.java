package com.hify.shared.resilience;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 熔断与重试的装配。
 *
 * <p>{@link CircuitBreakerService} 与 {@link LlmRetryPolicy} 本身不依赖 Spring 注解，
 * 可以脱离容器直接 new 出来用；这里只负责把它们注册成容器里的单例。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class ResilienceConfig {

    /**
     * LLM 调用的重试规则。
     *
     * @return 重试策略
     */
    @Bean
    public LlmRetryPolicy llmRetryPolicy() {
        return new LlmRetryPolicy();
    }

    /**
     * 按 provider 独立的熔断器。
     *
     * @param properties  熔断器参数
     * @param retryPolicy 重试规则
     * @return 熔断器服务
     */
    @Bean
    public CircuitBreakerService circuitBreakerService(CircuitBreakerProperties properties,
                                                       LlmRetryPolicy retryPolicy) {
        return new CircuitBreakerService(properties, retryPolicy);
    }
}
