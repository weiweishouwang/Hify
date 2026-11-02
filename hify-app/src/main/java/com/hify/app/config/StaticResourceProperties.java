package com.hify.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态资源配置。
 *
 * <p><b>静态资源与反代配置是 hify-app 的职责之一</b>（CLAUDE.md §2.1）：
 * 模块清单里只有它管「启动、装配、静态资源与反代配置」。
 *
 * <p>默认的位置是 classpath 上的几个标准目录，与 Spring Boot 默认的静态资源位置一致；
 * 额外可以挂一个外部目录（{@code file:/app/web/} 之类），
 * 让「一个 jar + 一个静态目录」这种单容器部署成为可能。
 *
 * <p><b>为什么位置可以指向外部目录</b>：前端不进 Maven reactor（CLAUDE.md §4），
 * 前端产物由 npm 构建、nginx 托管；把前端塞进后端镜像会把两条工具链焊死在一个
 * 生命周期里。但「运维不想再维护一个 nginx」是真实需求 ——
 * 所以这里支持把外部目录挂进来，而不是把构建搬进后端。
 */
@Component
@ConfigurationProperties(prefix = "hify.app.static")
public class StaticResourceProperties {

    /** 是否启用静态资源与单页应用回落。 */
    private boolean enabled = true;

    /**
     * 静态资源位置列表。
     *
     * <p>每个位置要以 {@code /} 结尾（Spring 的约定），没写会在装配时补上。
     * 前四个是 classpath 上的标准位置，最后一个留给外部目录。
     */
    private List<String> locations = new ArrayList<>(List.of(
            "classpath:/static/",
            "classpath:/public/",
            "classpath:/resources/",
            "classpath:/META-INF/resources/"));

    /** 单页应用的入口页；找不到静态文件时回落到它。 */
    private String indexPage = "index.html";

    /**
     * 不做回落的前缀。
     *
     * <p>这些前缀下的路径找不到资源时应当 404，而不是返回首页 HTML ——
     * 否则前端 {@code JSON.parse} 会炸在一个 HTML 页面上，报错还很难看懂。
     */
    private List<String> excludedPrefixes = new ArrayList<>(List.of("/api", "/actuator"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations == null ? new ArrayList<>() : locations;
    }

    public String getIndexPage() {
        return indexPage;
    }

    public void setIndexPage(String indexPage) {
        this.indexPage = indexPage;
    }

    public List<String> getExcludedPrefixes() {
        return excludedPrefixes;
    }

    public void setExcludedPrefixes(List<String> excludedPrefixes) {
        this.excludedPrefixes = excludedPrefixes == null ? new ArrayList<>() : excludedPrefixes;
    }
}
