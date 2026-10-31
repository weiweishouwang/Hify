package com.hify.ai.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** 启用模块内健康调度；任务使用独立命名的 UTC 时钟，便于确定性测试。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ProviderHealthConfig {
    @Bean("providerHealthClock")
    public Clock providerHealthClock() {
        return Clock.systemUTC();
    }
}
