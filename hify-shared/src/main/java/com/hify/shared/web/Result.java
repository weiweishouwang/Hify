package com.hify.shared.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hify.shared.exception.ErrorCode;

import java.io.Serializable;

/**
 * 统一响应封装。
 *
 * <p>所有对外 HTTP 接口的返回体都是该结构：{@code code} 表示业务结果，{@code message} 是面向用户的提示，
 * {@code data} 是业务数据。失败时 {@code data} 恒为 {@code null}。
 *
 * <p>业务失败必须靠错误码区分，不能靠 {@code message} 文本判断（见 CLAUDE.md §3.1 E4）。
 * 调用方判成功用 {@link #isOk()}，不要直接比较 code 字面量。
 *
 * <p>本类型位于 {@code shared.web} 而非 {@code shared.internal}：它是所有模块 controller 都要引用的
 * 公共契约，与 {@code shared.api} 同属对外可见面。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功码。约定 0 表示成功，非 0 表示失败。成功不是错误，因此不在 {@link ErrorCode} 中。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功时的默认提示信息。子类可用。 */
    protected static final String SUCCESS_MESSAGE = "ok";

    private final int code;

    private final String message;

    private final T data;

    /**
     * 构造响应。带 {@link JsonCreator} 是为了 Jackson 能反序列化（内部调用、测试断言都需要），
     * 每个参数都显式标注 {@link JsonProperty}，不依赖 {@code ParameterNamesModule}。
     */
    @JsonCreator
    public Result(@JsonProperty("code") int code,
                  @JsonProperty("message") String message,
                  @JsonProperty("data") T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无业务数据。 */
    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /** 成功，带业务数据。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /** 失败，使用错误码的默认文案。业务失败优先用这个重载。 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), null);
    }

    /** 失败，使用错误码但用自定义文案覆盖默认提示。 */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败，没有更精确的错误码时的兜底，错误码取 {@link ErrorCode#INTERNAL_ERROR}。
     *
     * <p>刻意不提供 {@code fail(int, String)} 重载——它允许调用点直接写错误码字面量，
     * 会让错误码散落到各处（见 CLAUDE.md §3.1 E6）。需要新的错误码时，
     * 在 {@link ErrorCode} 里加枚举常量，而不是在调用点造数字。
     */
    public static <T> Result<T> fail(String message) {
        return fail(ErrorCode.INTERNAL_ERROR, message);
    }

    /**
     * 是否成功。调用方一律用这个方法判断，不要直接比较 code 字面量。
     *
     * <p>{@code @JsonIgnore} 是必需的：{@code isXxx()} 会被 Jackson 当作布尔属性序列化，
     * 导致响应体多出一个 {@code "ok"} 字段。
     */
    @JsonIgnore
    public boolean isOk() {
        return code == SUCCESS_CODE;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Result{code=" + code + ", message='" + message + "', data=" + data + '}';
    }
}
