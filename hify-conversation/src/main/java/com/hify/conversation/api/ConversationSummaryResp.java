package com.hify.conversation.api;

import java.time.LocalDateTime;

/** 会话摘要：列表项用。 */
public class ConversationSummaryResp {

    /** 对外会话标识。 */
    private String publicId;

    /** 标题；没改过就是第一条用户消息的前若干字。 */
    private String title;

    /** 使用的 Agent 对外标识。 */
    private String agentPublicId;

    /** Agent 名称，仅用于展示。 */
    private String agentName;

    /** 消息数。 */
    private Integer messageCount;

    /** 最后一条消息时间(UTC)。 */
    private LocalDateTime lastMessageAt;

    /** 状态：active / archived。 */
    private String status;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAgentPublicId() {
        return agentPublicId;
    }

    public void setAgentPublicId(String agentPublicId) {
        this.agentPublicId = agentPublicId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
