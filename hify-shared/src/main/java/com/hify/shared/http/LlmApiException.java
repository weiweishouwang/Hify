package com.hify.shared.http;

import com.hify.shared.exception.BizException;

/**
 * 调用上游 LLM 服务失败时抛出的异常。
 *
 * <p>继承 {@link BizException}，因此能被全局异常处理器统一转成响应体，
 * HTTP 状态取 {@link LlmErrorKind#getErrorCode()} 里的状态。
 *
 * <p>失败原因用 {@link LlmErrorKind} 表达，不要靠 message 文本判断。
 *
 * <p><b>message 里不放上游响应体</b>——上游的报错文本可能夹带用户输入的内容，
 * 会随异常一路回显到终端用户那里（CLAUDE.md §3.1 L4）。上游响应体只记在服务端
 * 的 DEBUG 日志里，需要排查时开 DEBUG 级别即可。
 */
public class LlmApiException extends BizException {

    private static final long serialVersionUID = 1L;

    private final LlmErrorKind kind;

    /** 用错误分类的默认文案。 */
    public LlmApiException(LlmErrorKind kind) {
        super(kind.getErrorCode());
        this.kind = kind;
    }

    /** 用错误分类，但用自定义文案覆盖默认提示。 */
    public LlmApiException(LlmErrorKind kind, String message) {
        super(kind.getErrorCode(), message);
        this.kind = kind;
    }

    /** 用错误分类与自定义文案，并把原始异常作为 cause 保留堆栈。 */
    public LlmApiException(LlmErrorKind kind, String message, Throwable cause) {
        super(kind.getErrorCode(), message, cause);
        this.kind = kind;
    }

    /** 失败分类。 */
    public LlmErrorKind getKind() {
        return kind;
    }
}
