package com.hify.app.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * 单页应用回落解析器。
 *
 * <p>前端是 history 模式的路由 —— 刷新 {@code /agents} 会向服务器要
 * {@code /agents} 这个路径，服务器上并没有这个文件。没有回落就是 404，
 * 用户一刷新页面就白了。
 *
 * <p><b>解析顺序</b>：先按普通静态资源找（{@link PathResourceResolver} 的行为），
 * 找到了就返回原文件；找不到、且路径不在排除前缀里，才回落到入口页。
 *
 * <p><b>为什么要排除 {@code /api} 与 {@code /actuator}</b>：这些前缀下的路径
 * 找不到资源时应当是 404 JSON，而不是一份 HTML —— 前端 {@code JSON.parse}
 * 炸在一个 HTML 页面上，报错还很难看懂。
 */
class SpaFallbackResolver extends PathResourceResolver {

    private final StaticResourceProperties properties;

    SpaFallbackResolver(StaticResourceProperties properties) {
        this.properties = properties;
    }

    @Override
    protected Resource resolveResourceInternal(HttpServletRequest request, String path,
                                              List<? extends Resource> locations,
                                              ResourceResolverChain chain) {
        // 排除前缀下的路径不做回落，也不当静态资源找 ——
        // /api 下的路径找不到就该是 404，而不是一份 HTML。
        if (isExcluded(request)) {
            return null;
        }
        // 目录请求（"/" 或 "/assets/"）：交给入口页。
        // 不这么处理的话，资源位置指向的是一个目录，
        // 会被当成「目录存在」返回，前端拿到的是个空目录而不是首页。
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return indexResource(locations);
        }
        Resource resource = super.resolveResourceInternal(request, path, locations, chain);
        if (resource != null) {
            return resource;
        }
        return indexResource(locations);
    }

    /** 从各个位置里找入口页；一个都找不到就返回 null（交回 404）。 */
    private Resource indexResource(List<? extends Resource> locations) {
        if (locations == null) {
            return null;
        }
        for (Resource location : locations) {
            Resource index = relativeTo(location, properties.getIndexPage());
            if (index != null && index.exists() && index.isReadable()) {
                return index;
            }
        }
        return null;
    }

    /**
     * 在某个资源位置下取一个相对路径。
     *
     * <p>位置有两种形态：classpath 上的 {@link PathResource}（jar 里或目录里），
     * 以及指向外部目录的 {@link UrlResource}。前者用 {@code createRelative}，
     * 后者按 URL 解析相对路径。
     */
    private Resource relativeTo(Resource location, String relativePath) {
        if (location instanceof PathResource pathResource) {
            return pathResource.createRelative(relativePath);
        }
        try {
            URL url = location.getURL();
            return new UrlResource(new URL(url, relativePath));
        } catch (IOException exception) {
            return null;
        }
    }

    /** 请求路径是否在排除前缀里。 */
    private boolean isExcluded(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        for (String prefix : properties.getExcludedPrefixes()) {
            if (prefix == null || prefix.isEmpty()) {
                continue;
            }
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 去掉 context path 的请求路径。 */
    private String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
