package com.hify.shared.exception;

/**
 * 业务异常。
 *
 * <p>继承 {@link RuntimeException}（检查异常会强迫每个调用方写 catch，污染业务代码）。
 * 业务失败的原因由持有的 {@link ErrorCode} 表达，{@code message} 只是面向用户的提示文案，
 * 不能用来判断失败原因（见 CLAUDE.md §3.1 E4）。
 *
 * <p>三条使用纪律：
 * <ul>
 *   <li>抛出时优先用 {@link #BizException(ErrorCode)}，文案由错误码统一维护</li>
 *   <li>需要针对具体场景补充提示时用 {@link #BizException(ErrorCode, String)} 覆盖文案</li>
 *   <li>包装底层异常时必须用 {@link #BizException(ErrorCode, String, Throwable)}，
 *       把原始异常作为 cause 传进去——只传 {@code getMessage()} 会丢失堆栈（见 §3.1 E2）</li>
 * </ul>
 *
 * <p>本类型位于 {@code shared.exception} 而非 {@code shared.internal}：
 * 它是所有模块都要引用的公共契约，与 {@code shared.api} 同属对外可见面。
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /** 使用错误码的默认文案。 */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /** 使用错误码，但用自定义文案覆盖默认提示。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 使用错误码与自定义文案，并把原始异常作为 cause 保留堆栈。 */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** 使用错误码与自定义文案，并保留原始异常。 */
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
