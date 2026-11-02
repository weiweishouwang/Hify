package com.hify.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨域配置的装配。
 *
 * <p>要点只有两条：**默认关闭**，以及**启用时绝不带上通配来源**。
 */
class CorsConfigTest {

    @Test
    @DisplayName("没有配允许的来源时不注册任何规则")
    void disabledByDefault() {
        CorsProperties properties = new CorsProperties();
        assertThat(properties.isEnabled()).isFalse();

        TestCorsRegistry registry = new TestCorsRegistry();
        new CorsConfig(properties).addCorsMappings(registry);

        assertThat(registry.mappings()).isEmpty();
    }

    @Test
    @DisplayName("配了来源后规则挂在 /api/** 上，且不带通配来源")
    void registersApiPattern() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://hify.example.com"));

        TestCorsRegistry registry = new TestCorsRegistry();
        new CorsConfig(properties).addCorsMappings(registry);

        assertThat(registry.mappings()).containsKey("/api/**");

        CorsConfiguration configuration = registry.mappings().get("/api/**");
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://hify.example.com");
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        // 令牌走 Authorization 头，必须允许
        assertThat(configuration.getAllowedHeaders()).contains("*");
        // 带凭据 + 通配来源是浏览器直接拒绝的组合
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("空串不算来源：环境变量没配时不能变成一个空来源")
    void blankOriginIsIgnored() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("", "  ", "https://hify.example.com"));

        assertThat(properties.getAllowedOrigins()).containsExactly("https://hify.example.com");
        assertThat(properties.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("只有空串来源时视为未启用")
    void onlyBlankOriginsMeansDisabled() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of(""));

        assertThat(properties.isEnabled()).isFalse();

        TestCorsRegistry registry = new TestCorsRegistry();
        new CorsConfig(properties).addCorsMappings(registry);
        assertThat(registry.mappings()).isEmpty();
    }

    /** {@code CorsRegistry#getCorsConfigurations} 是 protected，用子类把它露出来。 */
    private static final class TestCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> mappings() {
            return getCorsConfigurations();
        }
    }
}
