package com.hify.shared.web;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>所有异常统一转成 {@link Result} 结构，错误码一律取自 {@link ErrorCode} 枚举，
 * **禁止在代码里硬编码错误码或 HTTP 状态码**——否则错误码表会散落到各处，无法统一维护。
 *
 * <p>响应体只回传面向用户的文案，异常细节（堆栈、内部信息）只进日志，不外泄（见 §3.1 L4）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：按其持有的 ErrorCode 返回，HTTP 状态码取自该错误码。
     *
     * <p>业务异常是预期内的失败，不打印堆栈——堆栈对定位业务问题没有帮助，只会淹没日志。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("业务异常, errorCode={}, message={}", errorCode.getCode(), e.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, e.getMessage()));
    }

    /**
     * {@code @RequestBody} 上的 {@code @Valid} 校验失败。
     *
     * <p>返回 PARAM_ERROR，并把逐字段的校验信息带进 message，便于前端直接展示。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return parameterError(e.getBindingResult());
    }

    /**
     * 表单 / 查询参数绑定校验失败（{@code @Valid @ModelAttribute} 等）。
     *
     * <p>{@code MethodArgumentNotValidException} 是它的子类，这里单独处理以覆盖非 body 的绑定场景。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        return parameterError(e.getBindingResult());
    }

    /**
     * 缺少必需的请求参数。
     *
     * <p>单独处理是为了把「缺少哪个参数」带进 message；否则只能回一句笼统的「Bad Request」。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        ErrorCode errorCode = ErrorCode.PARAM_ERROR;
        String message = errorCode.getDefaultMessage() + ": 缺少参数 " + e.getParameterName();
        log.warn("缺少必需参数, name={}", e.getParameterName());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, message));
    }

    /**
     * 请求体无法解析（JSON 语法错误、类型不匹配等）。
     *
     * <p>**必须单独处理**：{@code HttpMessageNotReadableException} 不实现 {@link ErrorResponse}，
     * 不会被下面的兜底逻辑识别，会被当成服务端故障返回 500——而它其实是最常见的客户端错误之一。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        ErrorCode errorCode = ErrorCode.PARAM_ERROR;
        log.warn("请求体解析失败, reason={}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, errorCode.getDefaultMessage() + ": 请求体格式错误"));
    }

    /**
     * 方法参数级校验失败（Spring 6.1+ 的 {@code @Validated} 方法参数校验）。
     *
     * <p>Spring 6.1 起，方法参数上的约束校验由 Spring MVC 自己完成并抛出
     * {@code HandlerMethodValidationException}，不再透出底层的
     * {@code ConstraintViolationException}——两者都要接，否则会漏。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException e) {
        ErrorCode errorCode = ErrorCode.PARAM_ERROR;
        String details = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("方法参数校验失败, detail={}", details);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, withDetails(errorCode, details)));
    }

    /**
     * 方法参数上的约束校验失败（类上 {@code @Validated} + 参数 {@code @NotBlank} 等）。
     *
     * <p>Spring MVC 路径下通常由上一条 {@code HandlerMethodValidationException} 接管；
     * 这里保留是为了非 MVC 入口（如手工校验、直接抛出）也能被正确映射。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ErrorCode errorCode = ErrorCode.PARAM_ERROR;
        String details = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数约束校验失败, detail={}", details);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, withDetails(errorCode, details)));
    }

    /**
     * 兜底：任何未被上面捕获的异常。
     *
     * <p>其中 Spring MVC 自身的异常（405 方法不支持、415 媒体类型不支持、404 未匹配路由、
     * 400 请求体无法解析等）都实现了 {@link ErrorResponse}，自带 HTTP 状态码，**必须单独识别**：
     * 否则它们会一律变成 500，让「客户端请求写错了」和「服务端出故障」无法区分。
     *
     * <p>这里用 {@code instanceof} 判断而不是 {@code @ExceptionHandler(ErrorResponse.class)}——
     * 后者不合法：注解参数必须是 {@code Throwable} 的子类，而 {@code ErrorResponse} 是接口。
     *
     * <p>非 Spring 语义的异常返回 INTERNAL_ERROR，**不把异常信息回传给调用方**
     * （内部信息外泄是安全事故），完整堆栈只写日志。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            ErrorCode errorCode = mapStatus(status);
            log.warn("请求处理失败, status={}, uri={}", status, request.getRequestURI());
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(Result.fail(errorCode));
        }
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        log.error("未捕获的异常, uri={}", request.getRequestURI(), e);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode));
    }

    /**
     * HTTP 状态码到错误码的映射。
     *
     * <p>有专属错误码的直接对应；其余 4xx 归为 PARAM_ERROR（客户端请求有问题），
     * 5xx 归为 INTERNAL_ERROR。**不硬编码错误码字面量**，一律取自枚举。
     *
     * <p>注意：405 这类状态在 {@link ErrorCode} 里没有专属常量，会落到 PARAM_ERROR，
     * 此时响应体的 {@code code} 只能反映错误族，精确状态要看 HTTP 状态码。
     */
    private ErrorCode mapStatus(int status) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null) {
            // 非标准状态码（HttpStatus 枚举里没有）：按 4xx/5xx 大类归类
            boolean clientError = status >= HttpStatus.BAD_REQUEST.value()
                    && status < HttpStatus.INTERNAL_SERVER_ERROR.value();
            return clientError ? ErrorCode.PARAM_ERROR : ErrorCode.INTERNAL_ERROR;
        }
        return switch (httpStatus) {
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case CONFLICT -> ErrorCode.CONFLICT;
            case TOO_MANY_REQUESTS -> ErrorCode.TOO_MANY_REQUESTS;
            default -> httpStatus.is4xxClientError() ? ErrorCode.PARAM_ERROR : ErrorCode.INTERNAL_ERROR;
        };
    }

    /** 校验失败统一出口：错误码固定为 PARAM_ERROR，message 带上逐字段明细。 */
    private ResponseEntity<Result<Void>> parameterError(BindingResult bindingResult) {
        ErrorCode errorCode = ErrorCode.PARAM_ERROR;
        String details = bindingResult.getAllErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败, detail={}", details);
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(Result.fail(errorCode, withDetails(errorCode, details)));
    }

    /** 单条校验错误的可读描述。 */
    private String describe(ObjectError error) {
        String message = Objects.requireNonNullElse(error.getDefaultMessage(), "");
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + (message.isEmpty() ? "" : ": " + message);
        }
        return message.isEmpty() ? "参数不合法" : message;
    }

    /** 有明细时把明细接在默认文案后面，没有明细时直接用默认文案。 */
    private String withDetails(ErrorCode errorCode, String details) {
        if (details.isEmpty()) {
            return errorCode.getDefaultMessage();
        }
        return errorCode.getDefaultMessage() + ": " + details;
    }
}
