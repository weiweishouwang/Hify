package com.hify.conversation.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 一轮对话的运行记录。
 *
 * <p><b>不继承 {@link com.hify.shared.mybatis.BaseEntity}</b>：事件流表，
 * 主键是 {@code (id, created_at)} 复合主键，也没有 {@code deleted} 列。
 */
@TableName("conv_run")
public class RunDO {

    /** 自增主键；与 created_at 组成复合主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 conv_session.id。 */
    @TableField("session_id")
    private Long sessionId;

    /** 使用的 Agent ai_agent.id。 */
    @TableField("agent_id")
    private Long agentId;

    /** 使用的模型 ai_model.id。 */
    @TableField("model_id")
    private Long modelId;

    /** 使用的接入点 ai_model_provider.id。 */
    @TableField("provider_id")
    private Long providerId;

    /** 状态：running / succeeded / failed。 */
    @TableField("status")
    private String status;

    /** 本轮输入。 */
    @TableField("input_text")
    private String inputText;

    /** 本轮输出。 */
    @TableField("output_text")
    private String outputText;

    /** 工具调用次数。 */
    @TableField("tool_call_count")
    private Integer toolCallCount;

    /** 输入 token。 */
    @TableField("prompt_tokens")
    private Integer promptTokens;

    /** 输出 token。 */
    @TableField("completion_tokens")
    private Integer completionTokens;

    /** 总 token。 */
    @TableField("total_tokens")
    private Integer totalTokens;

    /** 后端错误码。 */
    @TableField("error_code")
    private Integer errorCode;

    /** 失败原因。 */
    @TableField("error_message")
    private String errorMessage;

    /** 总耗时（毫秒）。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 创建时间(UTC) —— 分区键。 */
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

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }

    public Integer getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(Integer toolCallCount) {
        this.toolCallCount = toolCallCount;
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

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(Integer errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
