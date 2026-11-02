package com.hify.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置。
 *
 * <p><b>默认是关闭的</b>：允许的来源列表为空时不注册任何跨域规则。
 *
 * <p><b>为什么默认关闭</b>：部署拓扑有两条互斥的路由方案（CLAUDE.md §2.3）——
 * 方案 A 是「Ingress 把 {@code /api} 直接路由到 hify-api」，那时前后端不同源，
 * <b>api 侧必须配跨域</b>；方案 B 是「hify-web 内部的 nginx 反代 {@code /api}」，
 * 前后端同源，不需要跨域。开发环境走方案 B。
 *
 * <p>默认关闭意味着「不配就是同源行为」，不会有人误以为开了跨域就等于
 * 「谁都能调我的接口」。生产用方案 A 时设 {@code HIFY_CORS_ALLOWED_ORIGINS} 即可。
 *
 * <p><b>不用通配符</b>：令牌走 {@code Authorization} 头而不是 Cookie，
 * 不需要 {@code allowCredentials}；而「通配符 + 允许凭据」是浏览器直接拒绝的组合，
 * 写上去也不生效。想把接口开给任意站点，等于把内部工具的数据送出去。
 */
@Component
@ConfigurationProperties(prefix = "hify.cors")
public class CorsProperties {

    /** 允许跨域访问的来源，形如 {@code https://hify.example.com}；为空表示不启用跨域。 */
    private List<String> allowedOrigins = new ArrayList<>();

    /** 允许的 HTTP 方法。 */
    private List<String> allowedMethods = new ArrayList<>(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    /** 允许的请求头；{@code Authorization} 必须在内。 */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** 允许浏览器读取的响应头。 */
    private List<String> exposedHeaders = new ArrayList<>(List.of("Content-Disposition"));

    /** 预检结果的缓存秒数。 */
    private long maxAge = 3600L;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>();
        if (allowedOrigins == null) {
            return;
        }
        for (String origin : allowedOrigins) {
            // 环境变量没配时绑定来的是空串，空串不是合法的来源
            if (origin != null && !origin.isBlank()) {
                this.allowedOrigins.add(origin.trim());
            }
        }
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods == null ? new ArrayList<>() : allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders == null ? new ArrayList<>() : allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders == null ? new ArrayList<>() : exposedHeaders;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    /** 是否启用跨域。 */
    public boolean isEnabled() {
        return allowedOrigins != null && !allowedOrigins.isEmpty();
    }
}
