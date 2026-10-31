package com.hify.tool.api;

/**
 * 工具执行失败。
 *
 * <p>与 LLM 侧的 {@code LlmApiException} 分开：工具的失败原因与模型调用不是一回事，
 * 编排层要能分别处理。
 */
public class ToolExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ToolErrorKind kind;

    public ToolExecutionException(ToolErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ToolExecutionException(ToolErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /** 失败分类。 */
    public ToolErrorKind kind() {
        return kind;
    }
}
