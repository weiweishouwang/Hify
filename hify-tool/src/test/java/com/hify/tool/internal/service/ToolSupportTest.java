package com.hify.tool.internal.service;

import com.hify.shared.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 工具配置的校验规则。 */
class ToolSupportTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://api.example.com/weather",
            "http://localhost:8080/hook",
            "https://api.example.com/weather/"
    })
    @DisplayName("合法地址被规范化：去掉末尾斜杠")
    void validUrls(String value) {
        assertThat(ToolSupport.url(value)).doesNotEndWith("/");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://api.example.com/x",
            "api.example.com/x",
            "https://user:pass@api.example.com/x",
            "https://api.example.com/x?token=abc",
            "https://api.example.com/x#frag",
            ""
    })
    @DisplayName("非法地址被拒绝")
    void invalidUrls(String value) {
        assertThatThrownBy(() -> ToolSupport.url(value))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请求地址");
    }

    @ParameterizedTest
    @ValueSource(strings = {"weather_query", "current-time", "Tool2"})
    @DisplayName("工具标识允许字母数字下划线连字符")
    void validToolKeys(String value) {
        assertThat(ToolSupport.toolKey(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"天气", "tool key", "tool.key", ""})
    @DisplayName("非法工具标识被拒绝")
    void invalidToolKeys(String value) {
        assertThatThrownBy(() -> ToolSupport.toolKey(value)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("请求方法只支持 GET 与 POST，缺省 POST")
    void methods() {
        assertThat(ToolSupport.method(null)).isEqualTo("POST");
        assertThat(ToolSupport.method("get")).isEqualTo("GET");
        assertThatThrownBy(() -> ToolSupport.method("DELETE")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超时缺省 30 秒，超出范围被拒绝")
    void timeout() {
        assertThat(ToolSupport.timeoutMs(null)).isEqualTo(30_000);
        assertThatThrownBy(() -> ToolSupport.timeoutMs(500)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ToolSupport.timeoutMs(200_000)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("请求头里的空值与空名被丢弃")
    void headersDropBlanks() {
        var headers = ToolSupport.headers(java.util.Map.of(
                "Content-Type", "application/json",
                "X-Empty", "  ",
                "  ", "value"));
        assertThat(headers).containsOnlyKeys("Content-Type");
    }
}
