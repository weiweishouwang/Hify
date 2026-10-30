package com.hify.config;

import com.hify.shared.resilience.CircuitBreakerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 {@code application.yml} 里的熔断器配置能绑到 {@link CircuitBreakerProperties} 上。
 *
 * <p>yml 的键名写错时绑定会静默失败（拿到默认值），所以单独用这个测试兜住。
 */
class CircuitBreakerPropertiesBindingTest {

    @Test
    @DisplayName("application.yml 的熔断器参数能绑到属性类上")
    void bindsCircuitBreakerPropertiesFromApplicationYml() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        CircuitBreakerProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("hify.resilience.circuit-breaker", CircuitBreakerProperties.class)
                .orElseThrow(() -> new AssertionError("application.yml 里没有 hify.resilience.circuit-breaker 配置"));

        assertThat(properties.getSlidingWindowSize()).isEqualTo(10);
        assertThat(properties.getFailureRateThreshold()).isEqualTo(50);
        assertThat(properties.getWaitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
    }
}
