package com.hify.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用配置的关键项。
 *
 * <p>这些值都是「文档里点名要、代码里靠它活着」的东西 ——
 * 改错了不会立刻报错，只会在某个时刻表现为「流式莫名其妙断了」
 * 或「滚动更新时对话被拦腰截断」。所以在这里钉住。
 *
 * <p>顺带做一件事：<b>解析 application.yml</b>。YAML 的缩进错误在编译期看不出来，
 * 启动时才炸；这里加载一遍，配置写坏了测试就红。
 */
class AppConfigBindingTest {

    private final Environment environment = load();

    @Test
    @DisplayName("application.yml 能正常解析")
    void yamlParses() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("hify");
    }

    @Test
    @DisplayName("异步请求超时留足 30 分钟")
    void asyncRequestTimeout() {
        // 默认 30s 会掐断任何跑得更久的异步请求（工作流运行用的是 DeferredResult，
        // 没有 emitter 自己设的超时保护）
        assertThat(environment.getProperty("spring.mvc.async.request-timeout")).isEqualTo("30m");
    }

    @Test
    @DisplayName("优雅停机窗口与 K8s 的 terminationGracePeriodSeconds 对齐")
    void shutdownTimeout() {
        // 必须 ≥ 180s：Spring 先退完、容器再强杀的话，
        // 滚动更新时进行中的对话会被拦腰截断
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
                .isEqualTo("180s");
    }

    @Test
    @DisplayName("入站请求体上限 2MB")
    void maxInMemorySize() {
        assertThat(environment.getProperty("spring.codec.max-in-memory-size")).isEqualTo("2MB");
    }

    @Test
    @DisplayName("信任前置代理转发的协议与客户端 IP")
    void forwardHeaders() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
    }

    @Test
    @DisplayName("Tomcat 线程保持默认 200")
    void tomcatThreads() {
        assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class)).isEqualTo(200);
    }

    @Test
    @DisplayName("静态资源的默认位置与回落入口")
    void staticDefaults() {
        assertThat(environment.getProperty("hify.app.static.index-page")).isEqualTo("index.html");
        assertThat(environment.getProperty("hify.app.static.locations[0]")).isEqualTo("classpath:/static/");
    }

    @Test
    @DisplayName("跨域默认关闭：允许的来源为空")
    void corsDisabledByDefault() {
        // 环境变量没配 → 占位符解析成空串 → 空串不算来源 → 跨域不启用
        assertThat(environment.getProperty("hify.cors.allowed-origins")).isEmpty();
    }

    @Test
    @DisplayName("关掉 Spring Boot 的默认静态资源映射，只留自己这一份")
    void defaultResourceMappingsOff() {
        // 默认映射也注册在 /** 上，后注册的会顶掉先注册的 ——
        // 谁先谁后取决于装配顺序，不可靠
        assertThat(environment.getProperty("spring.web.resources.add-mappings", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("健康检查的探针分组：liveness 不带依赖，readiness 才带 db")
    void healthProbes() {
        assertThat(environment.getProperty("management.endpoint.health.group.liveness.include"))
                .isEqualTo("livenessState");
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db");
    }

    /** 加载 application.yml 到一个标准环境里 —— 占位符按环境变量解析。 */
    private Environment load() {
        StandardEnvironment environment = new StandardEnvironment();
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"));
        } catch (IOException exception) {
            throw new IllegalStateException("application.yml 读不出来", exception);
        }
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
