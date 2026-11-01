package com.hify.ai.api;

import java.util.List;

/**
 * 上下文里的一条消息。
 *
 * <p>四种角色：
 * <ul>
 *   <li>{@code system} —— 系统提示词</li>
 *   <li>{@code user} —— 用户输入</li>
 *   <li>{@code assistant} —— 上一轮模型输出，带 {@link #getToolCalls()} 时表示它要调工具</li>
 *   <li>{@code tool} —— 工具执行结果，靠 {@link #getToolCallId()} 对应回哪次调用</li>
 * </ul>
 *
 * <p>协议差异（Anthropic 把工具结果放在 user 消息里、OpenAI 放在 tool 消息里）
 * 由 ai 的适配器收敛，调用方只按这里的形状写。
 */
public class ChatMessage {

    /** 角色：system / user / assistant / tool。 */
    private String role;

    /** 正文。 */
    private String content;

    /** assistant 要调用的工具；为空表示不是工具调用轮。 */
    private List<ChatToolCall> toolCalls;

    /** tool 消息对应哪次工具调用。 */
    private String toolCallId;

    /** tool 消息的工具名。 */
    private String name;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /**
     * 工具执行结果消息。
     *
     * @param toolCallId 对应的工具调用 id
     * @param name       工具名
     * @param content    执行结果
     * @return 消息
     */
    public static ChatMessage tool(String toolCallId, String name, String content) {
        ChatMessage message = new ChatMessage("tool", content);
        message.setToolCallId(toolCallId);
        message.setName(name);
        return message;
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

    public List<ChatToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ChatToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
