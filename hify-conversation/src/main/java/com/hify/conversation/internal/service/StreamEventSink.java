package com.hify.conversation.internal.service;

/**
 * 流式对话的事件出口。
 *
 * <p>实现方把事件交给写出线程，由写出线程写进 SSE 响应。
 * 编排线程只管产出事件，不碰 servlet（CLAUDE.md §3.4：
 * 「往浏览器写帧的线程必须和 llmExecutor 分开」）。
 */
public interface StreamEventSink {

    /**
     * 发一个事件。
     *
     * @param event   事件名：message / tool_call / tool_result / done / error
     * @param payload 事件数据，会被序列化成 JSON
     */
    void emit(String event, Object payload);

    /**
     * 流是否已经结束（客户端断开、写出线程退出）。
     *
     * <p>编排线程要靠它尽早收手 —— 上游还在吐字，但客户端已经走了。
     *
     * @return 已关闭时为 true
     */
    boolean isClosed();
}
