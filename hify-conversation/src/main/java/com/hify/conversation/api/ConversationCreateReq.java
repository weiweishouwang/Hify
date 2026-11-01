package com.hify.conversation.api;

/**
 * 新建会话的请求。
 *
 * <p>会话一创建就绑一个 Agent —— 对话侧的模型、工具、知识库、提示词全由它决定。
 */
public class ConversationCreateReq {

    /** 使用的 Agent 对外标识。 */
    @jakarta.validation.constraints.NotBlank(message = "必须选择一个 Agent")
    @jakarta.validation.constraints.Size(max = 26, message = "Agent 标识格式不合法")
    private String agentPublicId;

    /** 会话标题；不填就取第一条用户消息的前 30 个字。 */
    @jakarta.validation.constraints.Size(max = 255, message = "标题最长 255 字符")
    private String title;

    public String getAgentPublicId() {
        return agentPublicId;
    }

    public void setAgentPublicId(String agentPublicId) {
        this.agentPublicId = agentPublicId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
