package com.hify.conversation.api;

import java.time.LocalDateTime;

/**
 * 一次对话运行的摘要（控制台日志页用）。
 *
 * <p>只给「看得懂」的信息：谁在聊、聊成什么样、花了多少 token、耗时多久。
 * 输入输出正文不在这里 —— 那是会话详情的正文，日志页要的是索引。
 */
public class ConversationRunSummaryResp {

    /** 运行主键。 */
    private Long id;

    /** 会话的对外 ID。 */
    private String sessionPublicId;

    /** 会话标题。 */
    private String sessionTitle;

    /** 使用的 Agent 名字。 */
    private String agentName;

    /** 状态：running / succeeded / failed。 */
    private String status;

    /** 输入 token 数。 */
    private Integer promptTokens;

    /** 输出 token 数。 */
    private Integer completionTokens;

    /** 工具调用次数。 */
    private Integer toolCallCount;

    /** 耗时（毫秒）。 */
    private Integer latencyMs;

    /** 失败原因；成功时为 null。 */
    private String errorMessage;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionPublicId() {
        return sessionPublicId;
    }

    public void setSessionPublicId(String sessionPublicId) {
        this.sessionPublicId = sessionPublicId;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(Integer toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
