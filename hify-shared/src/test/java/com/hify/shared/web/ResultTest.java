package com.hify.shared.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Result} 的单元测试。
 *
 * <p>重点锁两条曾经出过问题的约束：
 * <ul>
 *   <li>响应体只含 code / message / data —— {@code isOk()} 曾被 Jackson 当作布尔属性
 *       序列化成 {@code "ok":true} 混进响应体</li>
 *   <li>不存在第二个"服务端错误码" —— 曾有 {@code Result.ERROR_CODE = 500} 与
 *       {@code ErrorCode.INTERNAL_ERROR = 50000} 并存，同类错误发出两个码</li>
 * </ul>
 */
class ResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("ok() 返回成功码、无数据")
    void ok无数据() {
        Result<String> result = Result.ok();

        assertThat(result.getCode()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getData()).isNull();
        assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("ok(data) 携带业务数据")
    void ok带数据() {
        Result<String> result = Result.ok("hello");

        assertThat(result.getCode()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("fail(ErrorCode) 使用枚举码与默认文案")
    void fail用错误码() {
        Result<String> result = Result.fail(ErrorCode.NOT_FOUND);

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo(ErrorCode.NOT_FOUND.getDefaultMessage());
        assertThat(result.getData()).isNull();
        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("fail(ErrorCode, message) 用自定义文案覆盖默认文案")
    void fail用自定义文案() {
        Result<String> result = Result.fail(ErrorCode.NOT_FOUND, "Agent 不存在: a-1");

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo("Agent 不存在: a-1");
    }

    @Test
    @DisplayName("fail(String) 的兜底错误码必须来自 ErrorCode 枚举，不得自成一套")
    void fail裸消息走枚举兜底码() {
        Result<String> result = Result.fail("某个未归类的失败");

        assertThat(result.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("某个未归类的失败");
    }

    @Test
    @DisplayName("所有失败路径的错误码都必须能在 ErrorCode 枚举里找到")
    void 失败码均来自枚举() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(Result.fail(errorCode).getCode()).isEqualTo(errorCode.getCode());
            assertThat(Result.fail(errorCode, "自定义").getCode()).isEqualTo(errorCode.getCode());
        }
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode))
                .contains(Result.fail("兜底").getCode());
    }

    @Test
    @DisplayName("序列化后的响应体只包含 code / message / data 三个字段")
    void 序列化字段集合固定() throws Exception {
        Map<String, Object> body = serialize(Result.ok("x"));

        assertThat(body).containsOnlyKeys("code", "message", "data");
    }

    @Test
    @DisplayName("失败响应的 data 恒为 null")
    void 失败响应无数据() throws Exception {
        Map<String, Object> body = serialize(Result.fail(ErrorCode.PARAM_ERROR, "参数缺失"));

        assertThat(body).containsEntry("data", null);
        assertThat(body).containsEntry("code", ErrorCode.PARAM_ERROR.getCode());
    }

    @Test
    @DisplayName("可以反序列化回等价的 Result")
    void 反序列化往返() throws Exception {
        Result<String> original = Result.ok("payload");

        Result<String> restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), new TypeReference<Result<String>>() { });

        assertThat(restored.getCode()).isEqualTo(original.getCode());
        assertThat(restored.getMessage()).isEqualTo(original.getMessage());
        assertThat(restored.getData()).isEqualTo(original.getData());
    }

    private Map<String, Object> serialize(Result<?> result) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(result),
                new TypeReference<Map<String, Object>>() { });
    }
}
