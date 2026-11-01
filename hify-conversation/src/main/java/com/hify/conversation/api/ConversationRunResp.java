package com.hify.conversation.api;

/**
 * 一轮对话的运行统计。
 *
 * @param runId          运行记录标识
 * @param finishReason   结束原因：stop / length / tool_calls / error
 * @param toolCallCount  工具调用次数
 * @param promptTokens   输入 token
 * @param completionTokens 输出 token
 * @param totalTokens    总 token
 * @param latencyMs      总耗时（毫秒）
 * @param errorMessage   失败原因；成功时为 null
 */
public record ConversationRunResp(
        Long runId,
        String finishReason,
        Integer toolCallCount,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer latencyMs,
        String errorMessage) {
}
