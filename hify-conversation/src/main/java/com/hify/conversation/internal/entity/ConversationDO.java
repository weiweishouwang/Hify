package com.hify.conversation.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.shared.mybatis.BaseEntity;

import java.time.LocalDateTime;

/**
 * 会话的数据对象。
 *
 * <p>会话是<b>配置表</b>（有 {@code deleted}、有 {@code created_by}），
 * 所以继承 {@link BaseEntity}。
 *
 * <p>{@code agentId} 存的是 {@code ai_agent.id}（跨模块只存 id）——
 * 要用它组装 Agent 时走 {@code agent.api} 的按主键组装入口。
 */
@TableName("conv_session")
public class ConversationDO extends BaseEntity {

    /** 对外 ID，ULID。 */
    @TableField("public_id")
    private String publicId;

    /** 发起人 user.id。 */
    @TableField("user_id")
    private Long userId;

    /** 使用的 Agent ai_agent.id。 */
    @TableField("agent_id")
    private Long agentId;

    /** API 调用来源 api_key.id。 */
    @TableField("api_key_id")
    private Long apiKeyId;

    /** 会话标题。 */
    @TableField("title")
    private String title;

    /** 消息数（冗余计数）。 */
    @TableField("message_count")
    private Integer messageCount;

    /** 最后一条消息时间(UTC)。 */
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    /** 状态：active / archived。 */
    @TableField("status")
    private String status;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public void setApiKeyId(Long apiKeyId) {
        this.apiKeyId = apiKeyId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
}
