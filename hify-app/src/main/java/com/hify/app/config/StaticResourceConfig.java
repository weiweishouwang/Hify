package com.hify.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态资源与单页应用回落的装配。
 *
 * <p><b>静态资源是 hify-app 的职责</b>（CLAUDE.md §2.1：启动、装配、静态资源与反代配置）。
 * 这里把前端产物挂到 {@code /**} 上，并给未知路径兜一个入口页回落。
 *
 * <p><b>为什么用资源处理器而不是兜底 Controller</b>：资源处理器的 order 在
 * {@code RequestMappingHandlerMapping} 之后，<b>业务接口的映射优先命中</b>，
 * 不会出现「兜底 Controller 把业务接口吃掉」的经典事故。
 *
 * <p><b>为什么关掉 Spring Boot 的默认静态资源映射</b>
 * （{@code spring.web.resources.add-mappings: false}）：默认映射也注册在 {@code /**} 上，
 * 而后注册的会<b>顶掉</b>先注册的 —— 谁先谁后取决于自动配置与用户配置的装配顺序，
 * 不可靠。关掉它、只留自己这一份，回落行为才是确定的。关掉后那几个标准位置
 * （{@code classpath:/static/} 等）由本类的默认配置补回来。
 */
@Configuration(proxyBeanMethods = false)
public class StaticResourceConfig implements WebMvcConfigurer {

    /** 带协议前缀的位置（{@code classpath:} / {@code file:} / …）。 */
    private static final java.util.regex.Pattern SCHEME =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");

    private final StaticResourceProperties properties;

    public StaticResourceConfig(StaticResourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        List<String> locations = normalize(properties.getLocations());
        if (locations.isEmpty()) {
            return;
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(locations.toArray(new String[0]))
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver(properties));
    }

    /**
     * 根路径交给入口页。
     *
     * <p><b>为什么不能用资源处理器做这件事</b>：{@code /**} 匹配不到 {@code "/"} ——
     * 它匹配的是「/ 之后的路径」。而 {@code "/"} 这条映射传下来的路径是<b>空串</b>，
     * 资源解析器拿到空串就直接返回 null，连回落逻辑都进不去。
     *
     * <p>所以这里用视图控制器把 {@code "/"} 转发到入口页：
     * {@code forward:/index.html} 会被 {@code /**} 那条规则正常命中并返回文件。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (!properties.isEnabled() || normalize(properties.getLocations()).isEmpty()) {
            return;
        }
        registry.addViewController("/").setViewName("forward:/" + properties.getIndexPage());
    }

    /**
     * 位置归一化：补上 {@code file:} 前缀与结尾的 {@code /}。
     *
     * <p><b>为什么补前缀</b>：位置字符串是按 URL 解析的，
     * <b>没有协议前缀的路径会被当成「相对于 servlet 容器的路径」</b> ——
     * 传 {@code /app/web} 拿到的是 {@code ServletContextResource}，
     * 而不是一个真实的目录，文件一个都找不到。所以裸路径一律按文件目录处理。
     *
     * <p><b>为什么补结尾的 {@code /}</b>：Spring 的资源位置约定以 {@code /} 结尾表示「目录」，
     * 少了它会被当成文件路径。
     */
    private List<String> normalize(List<String> locations) {
        List<String> result = new ArrayList<>();
        if (locations == null) {
            return result;
        }
        for (String location : locations) {
            if (location == null || location.isBlank()) {
                continue;
            }
            String trimmed = location.trim();
            if (!SCHEME.matcher(trimmed).matches()) {
                trimmed = "file:" + (trimmed.startsWith("/") ? trimmed : "/" + trimmed);
            }
            result.add(trimmed.endsWith("/") ? trimmed : trimmed + "/");
        }
        return result;
    }
}
