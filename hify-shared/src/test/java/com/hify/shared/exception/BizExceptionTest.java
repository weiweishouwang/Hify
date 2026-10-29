package com.hify.shared.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link BizException} 的单元测试。 */
class BizExceptionTest {

    @Test
    @DisplayName("不传 message 时使用错误码的默认文案")
    void 默认文案() {
        BizException exception = new BizException(ErrorCode.PARAM_ERROR);

        assertThat(exception.getMessage()).isEqualTo(ErrorCode.PARAM_ERROR.getDefaultMessage());
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("自定义 message 覆盖默认文案")
    void 自定义文案() {
        BizException exception = new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: a-1");

        assertThat(exception.getMessage()).isEqualTo("Agent 不存在: a-1");
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("包装底层异常时保留原始 cause 与堆栈")
    void 保留Cause堆栈() {
        IllegalStateException cause = new IllegalStateException("底层炸了");

        BizException exception = new BizException(ErrorCode.UPSTREAM_ERROR, "调用 OpenAI 失败", cause);

        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause().getMessage()).isEqualTo("底层炸了");
        assertThat(exception.getCause().getStackTrace()).isNotEmpty();
    }

    @Test
    @DisplayName("cause 与错误码二选一时使用默认文案")
    void Cause加错误码() {
        IllegalStateException cause = new IllegalStateException("底层炸了");

        BizException exception = new BizException(ErrorCode.INTERNAL_ERROR, cause);

        assertThat(exception.getMessage()).isEqualTo(ErrorCode.INTERNAL_ERROR.getDefaultMessage());
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("是 RuntimeException，调用方不必强制 catch")
    void 是非受检异常() {
        assertThat(RuntimeException.class).isAssignableFrom(BizException.class);
    }

    @Test
    @DisplayName("失败原因由错误码承载，而不是靠 message 文本")
    void 失败原因靠错误码() {
        BizException exception = new BizException(ErrorCode.NOT_FOUND, "随便什么文案");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(exception.getErrorCode().getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
    }
}
