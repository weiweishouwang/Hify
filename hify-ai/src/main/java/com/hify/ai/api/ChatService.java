package com.hify.ai.api;

/**
 * 流式对话的公开契约。
 *
 * <p><b>纯能力层</b>：只做「把消息发给模型，把增量吐回来」。
 * 不检索、不拼上下文、不执行工具、不做工具循环 —— 那些是编排层的事
 * （CLAUDE.md §2.1 的模块不变量）。
 *
 * <p><b>调用是阻塞的</b>：要等上游把整条流吐完才返回。调用方要放在
 * {@code llmExecutor} 上跑，不要占 Tomcat 工作线程（CLAUDE.md §3.5）。
 */
public interface ChatService {

    /**
     * 流式对话。
     *
     * @param request  对话请求
     * @param listener 增量回调，跑在调用方线程上
     * @throws com.hify.shared.http.LlmApiException 上游不可达、鉴权失败或响应结构无效
     */
    void stream(ChatRequest request, ChatListener listener);
}
