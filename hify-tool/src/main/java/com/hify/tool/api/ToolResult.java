package com.hify.tool.api;

import java.util.Map;

/**
 * 工具执行结果。
 *
 * @param success      是否成功
 * @param content      成功时给模型看的内容
 * @param errorMessage 失败时的可读原因，成功时为 null
 * @param metadata     附加信息，例如耗时、截断标记
 */
public record ToolResult(
        boolean success,
        String content,
        String errorMessage,
        Map<String, Object> metadata) {

    public static ToolResult ok(String content) {
        return new ToolResult(true, content, null, Map.of());
    }

    public static ToolResult ok(String content, Map<String, Object> metadata) {
        return new ToolResult(true, content, null, metadata);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage, Map.of());
    }
}
