package com.hify.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 跨域装配。
 *
 * <p><b>什么时候才需要它</b>：部署拓扑方案 A（Ingress 把 {@code /api} 直接路由到
 * hify-api）下前端与 api 不同源，浏览器会拦下跨域请求，这时 api 侧必须配跨域。
 * 方案 B（nginx 反代，前端与 api 同源）不需要 —— 开发环境就是方案 B。
 *
 * <p>规则只挂在 {@code /api/**} 上：actuator 是运维端点（由 Ingress 与
 * NetworkPolicy 把关，不该从浏览器访问），静态资源是同源的，没有跨域需求。
 *
 * <p>允许的来源为空时**不注册任何规则** —— 跨域默认关闭，配置了才打开。
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebMvcConfigurer {

    /** 跨域规则作用的前缀。 */
    private static final String API_PATTERN = "/api/**";

    private final CorsProperties properties;

    public CorsConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!properties.isEnabled()) {
            return;
        }
        registry.addMapping(API_PATTERN)
                .allowedOrigins(toArray(properties.getAllowedOrigins()))
                .allowedMethods(toArray(properties.getAllowedMethods()))
                .allowedHeaders(toArray(properties.getAllowedHeaders()))
                .exposedHeaders(toArray(properties.getExposedHeaders()))
                // 令牌走 Authorization 头，不走 Cookie，所以不需要带凭据。
                // 允许凭据 + 通配来源是浏览器直接拒绝的组合，这里不提供这个开关。
                .allowCredentials(false)
                .maxAge(properties.getMaxAge());
    }

    private String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(new String[0]);
    }
}
