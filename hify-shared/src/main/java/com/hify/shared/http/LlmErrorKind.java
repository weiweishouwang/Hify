package com.hify.shared.http;

import com.hify.shared.exception.ErrorCode;

/**
 * 上游 LLM 调用失败的分类。
 *
 * <p>失败原因靠这个枚举区分，**不靠 message 文本判断**（CLAUDE.md §3.1 E4）。
 * 每个分类映射到一个 {@link ErrorCode}，异常最终被全局异常处理器转成响应体时，
 * HTTP 状态取该错误码的状态。
 *
 * <p>上游报 401/403 时归到 {@link #AUTH_FAILED} 而不是 {@code ErrorCode.UNAUTHORIZED}：
 * 被拒的是**我们调上游用的那把 API Key**，不是终端用户——返回 401 会让前端以为
 * 自己没登录，从而误触发重新登录流程。
 */
public enum LlmErrorKind {

    /** 请求超时：连接超时或读超时。 */
    TIMEOUT(ErrorCode.UPSTREAM_TIMEOUT),

    /** 上游拒绝凭证：API Key 无效、过期，或没有该模型的权限。 */
    AUTH_FAILED(ErrorCode.UPSTREAM_ERROR),

    /** 上游限流：请求太频繁（HTTP 429）。 */
    RATE_LIMITED(ErrorCode.TOO_MANY_REQUESTS),

    /** 其他上游故障：5xx、连接失败、响应不是 SSE 等。 */
    UPSTREAM_ERROR(ErrorCode.UPSTREAM_ERROR);

    private final ErrorCode errorCode;

    LlmErrorKind(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    /** 对应的业务错误码。 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
