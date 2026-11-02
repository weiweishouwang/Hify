package com.hify.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 外部目录里的静态资源。
 *
 * <p>「单容器部署」用得上：把前端产物挂进容器的一个目录，让 jar 自己托管，
 * 不用再维护一个 nginx。
 *
 * <p>顺带钉住一件事：**外部目录的写法**。classpath 上的位置带 {@code classpath:} 前缀，
 * 外部目录要带 {@code file:} —— 位置字符串是按 URL 解析的。
 */
class ExternalStaticDirTest {

    /**
     * 静态位置。
     *
     * <p>用静态字段而不是构造器参数：{@code @EnableWebMvc} 的装配要在上下文 refresh 时
     * 发生，那时 {@code @TempDir} 已经建好了，但配置类拿不到测试方法的参数。
     */
    private static String staticLocation;

    @Test
    @DisplayName("外部目录里的文件能取到，未知路径回落到入口页")
    void servesExternalDirectory(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "EXTERNAL_INDEX");
        Files.writeString(tempDir.resolve("app.js"), "console.log('external')");
        staticLocation = tempDir.toUri().toString();

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestApp.class);
        context.refresh();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("console.log('external')")));

        // 未知路径回落到外部目录里的入口页
        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("EXTERNAL_INDEX")));
    }

    @Test
    @DisplayName("位置写成裸路径时也能取到文件")
    void servesBarePathLocation(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "EXTERNAL_INDEX");
        staticLocation = tempDir.toString() + "/";

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestApp.class);
        context.refresh();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("EXTERNAL_INDEX")));
    }

    @Configuration
    @EnableWebMvc
    static class TestApp {

        @Bean
        StaticResourceProperties staticResourceProperties() {
            StaticResourceProperties properties = new StaticResourceProperties();
            properties.setLocations(List.of(staticLocation));
            return properties;
        }

        @Bean
        StaticResourceConfig staticResourceConfig(StaticResourceProperties properties) {
            return new StaticResourceConfig(properties);
        }
    }
}
