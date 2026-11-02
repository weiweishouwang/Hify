package com.hify.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 静态资源与单页应用回落的装配。
 *
 * <p>用一个最小化的 Spring MVC 上下文验证三件事：
 * <ol>
 *   <li>真实静态文件照常返回</li>
 *   <li>未知路径回落到入口页（前端是 history 路由，刷新 {@code /agents} 会要这个路径）</li>
 *   <li><b>业务接口不被资源处理器吃掉</b>，{@code /api} 下的未知路径是 404 而不是首页</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = StaticResourceConfigTest.TestApp.class)
class StaticResourceConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("真实静态文件原样返回")
    void servesRealStaticFile() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("console.log('hify')")));
    }

    @Test
    @DisplayName("未知路径回落到入口页")
    void fallsBackToIndex() throws Exception {
        mockMvc.perform(get("/agents"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HIFY_INDEX")));
    }

    @Test
    @DisplayName("根路径转发到入口页")
    void rootServesIndex() throws Exception {
        // "/**" 匹配不到 "/"（它匹配的是 "/" 之后的路径），
        // 所以根路径走视图控制器转发到 index.html
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("业务接口优先命中控制器，不被资源处理器吃掉")
    void apiMappingWins() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("probe"));
    }

    @Test
    @DisplayName("/api 下的未知路径是 404，不是首页")
    void unknownApiPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"code\":40400}"));
    }

    @Test
    @DisplayName("actuator 下的未知路径也是 404")
    void unknownActuatorPathIsNotFound() throws Exception {
        mockMvc.perform(get("/actuator/unknown"))
                .andExpect(status().isNotFound());
    }

    @Configuration
    @EnableWebMvc
    static class TestApp {

        @Bean
        StaticResourceProperties staticResourceProperties() {
            StaticResourceProperties properties = new StaticResourceProperties();
            properties.setLocations(List.of("classpath:/static/"));
            return properties;
        }

        @Bean
        StaticResourceConfig staticResourceConfig(StaticResourceProperties properties) {
            return new StaticResourceConfig(properties);
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        ProbeExceptionHandler probeExceptionHandler() {
            return new ProbeExceptionHandler();
        }
    }

    /** 探针接口：验证业务映射优先于静态资源处理器。 */
    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "probe";
        }
    }

    /** 把「找不到资源」翻译成 JSON 404，与生产里全局异常处理器的行为一致。 */
    @RestControllerAdvice
    static class ProbeExceptionHandler {

        @ExceptionHandler(Exception.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, Object> handle(Exception exception) {
            return Map.of("code", 40400);
        }
    }
}
