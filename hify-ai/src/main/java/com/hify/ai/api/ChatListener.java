package com.hify.ai.api;

/**
 * 流式对话的增量回调。
 *
 * <p><b>回调跑在调用方的线程上</b>（上游读是阻塞的，调用方本来就把它放在
 * {@code llmExecutor} 上跑）。所以回调里不要做慢操作 ——
 * 要写库、要发帧，交给编排层自己安排。
 */
public interface ChatListener {

    /**
     * 收到一段增量文本。
     *
     * @param delta 本次新增的文本片段
     */
    void onText(String delta);

    /**
     * 模型要调用工具。
     *
     * <p><b>参数已经拼完整了才回调</b> —— 上游的 arguments 是分片到达的，
     * 拼装在 ai 模块内部完成，调用方拿到的一定是完整的 JSON 字符串。
     *
     * @param call 工具调用
     */
    void onToolCall(ChatToolCall call);

    /**
     * token 用量。上游不一定给，给不了就不回调。
     *
     * @param usage 用量
     */
    default void onUsage(ChatUsage usage) {
    }

    /**
     * 结束了。
     *
     * @param finishReason 结束原因：stop / length / tool_calls / content_filter
     */
    void onFinish(String finishReason);
}
