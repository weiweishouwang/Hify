package com.hify.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * <p>错误码按 HTTP 状态码分段，便于识别错误族并为具体业务留出扩展空间：
 * <ul>
 *   <li>4xxxx —— 客户端错误（参数、认证、授权、资源、冲突、限流）</li>
 *   <li>5xxxx —— 服务端错误（内部异常、上游异常、上游超时）</li>
 * </ul>
 * 约定 {@code 0} 表示成功（非本枚举，见 {@code Result.SUCCESS_CODE}）。
 *
 * <p>业务失败必须靠错误码区分，不靠 message 文本判断（见 CLAUDE.md §3.1 E4）。
 * 需要更细的场景时，在对应分段内新增枚举常量（如 {@code PARAM_ERROR_2 = 40002}），
 * 不要复用已有常量再改文案。
 *
 * <p>本枚举位于 {@code shared.exception} 而非 {@code shared.internal}：
 * 它是所有模块都要引用的公共契约，与 {@code shared.api} 同属对外可见面。
 */
public enum ErrorCode {

    /** 参数不合法。 */
    PARAM_ERROR(40000, HttpStatus.BAD_REQUEST, "参数不合法"),

    /** 未登录、会话过期或凭证无效。 */
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),

    /** 已登录但无权访问该资源。 */
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权访问该资源"),

    /** 资源不存在。 */
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),

    /** 资源状态冲突，如重复创建、并发修改。 */
    CONFLICT(40900, HttpStatus.CONFLICT, "资源状态冲突"),

    /** 触发限流。 */
    TOO_MANY_REQUESTS(42900, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),

    /** 服务内部异常，兜底用。 */
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误"),

    /** 上游依赖异常，如 LLM provider、MCP server 返回错误。 */
    UPSTREAM_ERROR(50200, HttpStatus.BAD_GATEWAY, "上游服务异常"),

    /** 上游依赖超时。 */
    UPSTREAM_TIMEOUT(50400, HttpStatus.GATEWAY_TIMEOUT, "上游服务超时");

    private final int code;

    private final HttpStatus httpStatus;

    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /** 业务错误码。写入响应体，调用方据此判断失败原因。 */
    public int getCode() {
        return code;
    }

    /** 建议的 HTTP 状态码。全局异常处理器据此设置响应状态。 */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /** 默认提示文案。可被 {@code BizException} 的自定义 message 覆盖。 */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
