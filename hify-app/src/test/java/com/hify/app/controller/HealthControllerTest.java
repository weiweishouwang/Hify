package com.hify.app.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康检查接口。
 *
 * <p>它是给调用方看的（返回统一的 Result 结构），
 * 与给 K8s 探针用的 {@code /actuator/health} 不是一回事。
 */
class HealthControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

    @Test
    @DisplayName("返回统一的 Result 结构")
    void health() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").value("Hify is running"));
    }
}
