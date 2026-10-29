package com.hify.shared.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * {@link GlobalExceptionHandler} 的单元测试。
 *
 * <p>重点锁三条曾经出过问题的行为：
 * <ul>
 *   <li>Spring MVC 自身的异常（405 等）曾被兜底吞成 500 INTERNAL_ERROR —— 客户端
 *       请求写错和服务端出故障必须分得开</li>
 *   <li>请求体 JSON 损坏曾返回 500 —— {@code HttpMessageNotReadableException}
 *       不实现 {@code ErrorResponse}，不会被兜底里的 instanceof 识别</li>
 *   <li>兜底响应不得回显内部异常信息（曾担心泄露口令、连接串）</li>
 * </ul>
 */
class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("业务异常返回其错误码对应的 HTTP 状态与业务码")
    void 业务异常() throws Exception {
        MvcResult result = mockMvc.perform(get("/biz")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(ErrorCode.NOT_FOUND.getHttpStatus().value());
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("code", ErrorCode.NOT_FOUND.getCode());
        assertThat(body).containsEntry("message", "Agent 不存在: a-1");
    }

    @Test
    @DisplayName("@Valid 校验失败返回 PARAM_ERROR 并带字段明细")
    void 参数校验失败() throws Exception {
        MvcResult result = mockMvc.perform(post("/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("code", ErrorCode.PARAM_ERROR.getCode());
        assertThat((String) body.get("message")).contains("name").contains("不能为空");
    }

    @Test
    @DisplayName("缺少必需参数返回 PARAM_ERROR 并指出缺哪个参数")
    void 缺少必需参数() throws Exception {
        MvcResult result = mockMvc.perform(get("/need")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("code", ErrorCode.PARAM_ERROR.getCode());
        assertThat((String) body.get("message")).contains("must");
    }

    @Test
    @DisplayName("请求体 JSON 损坏返回 400 而不是 500")
    void 请求体损坏() throws Exception {
        MvcResult result = mockMvc.perform(post("/valid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result)).containsEntry("code", ErrorCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("Spring MVC 自身的 405 不被兜底吞成 500")
    void 方法不允许() throws Exception {
        MvcResult result = mockMvc.perform(post("/biz")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(405);
    }

    @Test
    @DisplayName("兜底异常返回 INTERNAL_ERROR，且不回显内部异常信息")
    void 兜底异常不回显内部信息() throws Exception {
        MvcResult result = mockMvc.perform(get("/boom")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        Map<String, Object> body = body(result);
        assertThat(body).containsEntry("code", ErrorCode.INTERNAL_ERROR.getCode());
        assertThat((String) body.get("message")).doesNotContain("db password");
        assertThat((String) body.get("message")).doesNotContain("内部炸了");
    }

    @Test
    @DisplayName("所有异常响应的业务码都能在 ErrorCode 枚举里找到，不允许硬编码")
    void 响应码全部来自枚举() throws Exception {
        Set<Integer> knownCodes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        MvcResult[] results = {
                mockMvc.perform(get("/biz")).andReturn(),
                mockMvc.perform(get("/boom")).andReturn(),
                mockMvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn(),
                mockMvc.perform(get("/need")).andReturn(),
                mockMvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{bad")).andReturn(),
                mockMvc.perform(post("/biz")).andReturn(),
        };

        for (MvcResult result : results) {
            Object code = body(result).get("code");
            assertThat(code).as("响应体 code 必须是 ErrorCode 枚举中的值").isInstanceOf(Integer.class);
            assertThat(knownCodes).contains((Integer) code);
        }
    }

    @Test
    @DisplayName("所有异常响应体都是 Result 结构")
    void 响应体结构统一() throws Exception {
        MvcResult result = mockMvc.perform(get("/biz")).andReturn();

        assertThat(body(result)).containsOnlyKeys("code", "message", "data");
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() { });
    }

    @RestController
    @Validated
    static class TestController {

        static class Payload {
            @NotBlank(message = "不能为空")
            private String name;
        }

        @GetMapping("/biz")
        String biz() {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: a-1");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("内部炸了: db password=xxx");
        }

        @PostMapping("/valid")
        String valid(@Valid @RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/need")
        String need(@RequestParam("must") String must) {
            return "ok";
        }
    }
}
