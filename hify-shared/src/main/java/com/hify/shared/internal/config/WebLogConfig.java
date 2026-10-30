package com.hify.shared.internal.config;

import com.hify.shared.log.RequestLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 请求日志的装配。
 *
 * <p>放在 {@code shared.internal} 里：它是共享内核自己的内部接线，
 * 别的模块 import 不到（跨模块只能走对方的 {@code api} 包，CLAUDE.md §3.2）。
 */
@Configuration(proxyBeanMethods = false)
public class WebLogConfig implements WebMvcConfigurer {

    private final RequestLogInterceptor requestLogInterceptor;

    public WebLogConfig(RequestLogInterceptor requestLogInterceptor) {
        this.requestLogInterceptor = requestLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestLogInterceptor).addPathPatterns("/**");
    }
}
