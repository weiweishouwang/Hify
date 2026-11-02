package com.hify.ai.api;

import java.util.List;
import java.util.Map;

/**
 * 流式对话的请求。
 *
 * <p><b>纯能力层</b>：只说「用这个模型，把这些消息和工具发过去」，
 * 不做检索、不做上下文拼接、不做工具循环 —— 那些是编排层的事
 * （CLAUDE.md §2.1 的模块不变量）。
 */
public class ChatRequest {

    /** 模型的对外标识（{@code ai_model.public_id}）。 */
    private String modelPublicId;

    /**
     * 调用者用户 id。
     *
     * <p><b>用来做「每用户每 provider 在途上限」的限流</b>（CLAUDE.md §3.5）：
     * 一个人开多个标签页，不能让他把整个 LLM 连接池吃光。
     *
     * <p><b>显式携带，不靠 ThreadLocal</b> —— 调用跑在 {@code llmExecutor} 上，
     * 那边的 {@code CurrentUserHolder} 是空的（线程池会串号）。
     * 没有用户上下文时（比如后台任务）可以不填，此时不做限流。
     */
    private Long userId;

    /** 上下文消息，按时间顺序。 */
    private List<ChatMessage> messages;

    /** 可供模型调用的工具；为空表示不带工具。 */
    private List<ChatTool> tools;

    /** 生成参数：temperature / max_tokens / top_p。 */
    private Map<String, Object> params;

    public String getModelPublicId() {
        return modelPublicId;
    }

    public void setModelPublicId(String modelPublicId) {
        this.modelPublicId = modelPublicId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public List<ChatTool> getTools() {
        return tools;
    }

    public void setTools(List<ChatTool> tools) {
        this.tools = tools;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
