package com.hify.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ErrorCode} 的单元测试：锁住错误码表的分段约定与唯一性。 */
class ErrorCodeTest {

    @Test
    @DisplayName("错误码不重复")
    void 错误码唯一() {
        Set<Integer> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).hasSameSizeAs(ErrorCode.values());
    }

    @Test
    @DisplayName("错误码的前三位就是它的 HTTP 状态码")
    void 错误码前三位等于HTTP状态() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getCode() / 100)
                    .as("%s 的错误码前三位应等于其 httpStatus", errorCode)
                    .isEqualTo(errorCode.getHttpStatus().value());
        }
    }

    @Test
    @DisplayName("每个错误码都有非空默认文案")
    void 默认文案非空() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.getDefaultMessage()).as("%s 缺少默认文案", errorCode).isNotBlank();
        }
    }

    @Test
    @DisplayName("枚举成员名全大写，单词间用下划线")
    void 成员名符合命名规范() {
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(errorCode.name()).as("%s 命名不符合规范", errorCode)
                    .matches("[A-Z][A-Z0-9]*(_[A-Z0-9]+)*");
        }
    }

    @Test
    @DisplayName("成功码不属于错误码枚举")
    void 成功码不在枚举里() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode))
                .doesNotContain(0);
    }
}
