package com.hify.shared.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link PageResult} 的单元测试。 */
class PageResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("of() 携带当前页记录与分页信息")
    void of带分页信息() {
        PageResult<String> result = PageResult.of(List.of("a", "b", "c"), 137L, 2, 3);

        assertThat(result.getCode()).isEqualTo(Result.SUCCESS_CODE);
        assertThat(result.getData()).containsExactly("a", "b", "c");
        assertThat(result.getTotal()).isEqualTo(137L);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("是 Result 的子类，可当统一响应体使用")
    void 是Result子类() {
        PageResult<String> result = PageResult.of(List.of("a"), 1L, 1, 10);

        assertThat(result).isInstanceOf(Result.class);
        assertThat(Result.class).isAssignableFrom(PageResult.class);
    }

    @Test
    @DisplayName("序列化后的响应体包含 code / message / data / total / page / size")
    void 序列化字段完整() throws Exception {
        String json = objectMapper.writeValueAsString(PageResult.of(List.of("a"), 137L, 2, 3));

        Map<String, Object> body = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });

        assertThat(body).containsOnlyKeys("code", "message", "data", "total", "page", "size");
        assertThat(body).containsEntry("total", 137);
        assertThat(body).containsEntry("page", 2);
        assertThat(body).containsEntry("size", 3);
    }

    @Test
    @DisplayName("可以反序列化回等价的 PageResult")
    void 反序列化往返() throws Exception {
        PageResult<String> original = PageResult.of(List.of("a", "b"), 10L, 1, 2);

        PageResult<String> restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), new TypeReference<PageResult<String>>() { });

        assertThat(restored.getTotal()).isEqualTo(10L);
        assertThat(restored.getPage()).isEqualTo(1);
        assertThat(restored.getSize()).isEqualTo(2);
        assertThat(restored.getData()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("空页也能正常表达")
    void 空页() {
        PageResult<String> result = PageResult.of(List.of(), 0L, 1, 10);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("错误码取自 ErrorCode 枚举")
    void 错误码来自枚举() {
        assertThat(ErrorCode.values()).isNotEmpty();
        assertThat(Result.SUCCESS_CODE).isZero();
    }
}
