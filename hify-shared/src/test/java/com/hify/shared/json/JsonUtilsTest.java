package com.hify.shared.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link JsonUtils} 的单元测试。 */
class JsonUtilsTest {

    private final JsonUtils jsonUtils = new JsonUtils(new ObjectMapper());

    record Payload(String name, int count) {
    }

    @Test
    @DisplayName("序列化与反序列化可往返")
    void 往返() {
        String json = jsonUtils.toJson(new Payload("张三", 3));

        Payload restored = jsonUtils.fromJson(json, Payload.class);

        assertThat(restored.name()).isEqualTo("张三");
        assertThat(restored.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("支持泛型类型")
    void 泛型() {
        String json = jsonUtils.toJson(List.of("a", "b"));

        List<String> restored = jsonUtils.fromJson(json, new TypeReference<List<String>>() { });

        assertThat(restored).containsExactly("a", "b");
    }

    @Test
    @DisplayName("解析为 Map")
    void 解析为Map() {
        Map<String, Object> map = jsonUtils.toMap("{\"a\":1}");

        assertThat(map).containsEntry("a", 1);
    }

    @Test
    @DisplayName("JSON 非法时抛 PARAM_ERROR")
    void 非法JSON() {
        assertThatThrownBy(() -> jsonUtils.fromJson("{bad json", Payload.class))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("类型不匹配时抛 PARAM_ERROR")
    void 类型不匹配() {
        assertThatThrownBy(() -> jsonUtils.fromJson("\"a string\"", Payload.class))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("序列化失败时抛 INTERNAL_ERROR")
    void 序列化失败() {
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public Object getBoom() {
                throw new IllegalStateException("boom");
            }
        };

        assertThatThrownBy(() -> jsonUtils.toJson(unserializable))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    @DisplayName("复用注入的 ObjectMapper 实例")
    void 复用注入实例() {
        ObjectMapper injected = new ObjectMapper();

        assertThat(new JsonUtils(injected).mapper()).isSameAs(injected);
    }
}
