package com.hify.conversation.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条消息。
 *
 * <p>工具调用消息（角色 {@code tool}）的 {@code content} 是结果正文，
 * 调用的名字与入参在 {@code toolCalls} 里 —— 库里的 {@code content_type=json}
 * 存的是结构化文档，出接口时拆成这两部分。
 */
public class ConversationMessageResp {

    /** 消息标识（数值主键）。 */
    private Long id;

    /** 角色：user / assistant / system / tool。 */
    private String role;

    /** 正文。 */
    private String content;

    /** 正文类型：text / json。 */
    private String contentType;

    /** 结束原因：stop / length / tool_calls / error。 */
    private String finishReason;

    /** 本条消息的 token 数。 */
    private Integer tokenCount;

    /** 生成耗时（毫秒）。 */
    private Integer latencyMs;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 工具调用明细；只有工具消息有。 */
    private List<ToolCallView> toolCalls;

    /**
     * 一次工具调用的展示形态。
     *
     * @param id        调用标识
     * @param name      工具名
     * @param arguments 入参 JSON
     * @param result    执行结果
     * @param error     失败原因；成功时为 null
     */
    public record ToolCallView(String id, String name, String arguments, String result, String error) {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
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

    public List<ToolCallView> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallView> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
