package com.hify.conversation.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 消息的数据对象。
 *
 * <p><b>不继承 {@link com.hify.shared.mybatis.BaseEntity}</b>：消息是事件流表，
 * 主键是 {@code (id, created_at)} 复合主键，而且没有 {@code deleted} 列 ——
 * 继承基类会让 MyBatis-Plus 给每条 SQL 追加 {@code deleted = 0}，直接语法错误
 * （CLAUDE.md §3.3）。
 *
 * <p>工具调用消息（角色 {@code tool}）的 {@code content} 是一段 JSON：
 * {@code {"toolCallId":..,"name":..,"arguments":..,"result":..,"error":..}}。
 */
@TableName("conv_message")
public class MessageDO {

    /** 自增主键；与 created_at 组成复合主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 conv_session.id。 */
    @TableField("session_id")
    private Long sessionId;

    /** 角色：user / assistant / system / tool。 */
    @TableField("role")
    private String role;

    /** 消息正文。 */
    @TableField("content")
    private String content;

    /** 正文类型：text / json。 */
    @TableField("content_type")
    private String contentType;

    /** 生成该消息的模型 ai_model.id。 */
    @TableField("model_id")
    private Long modelId;

    /** 生成该消息的接入点 ai_model_provider.id。 */
    @TableField("provider_id")
    private Long providerId;

    /** 本条消息的 token 数。 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 结束原因：stop / length / tool_calls / error。 */
    @TableField("finish_reason")
    private String finishReason;

    /** 生成耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /**
     * 创建时间(UTC) —— <b>分区键</b>。
     *
     * <p>由应用按 UTC 填充，与分区边界保持一致；落在没有月份分区的月份里
     * 会被 {@code pmax} 兜住。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
