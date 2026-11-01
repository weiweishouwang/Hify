package com.hify.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发送一条消息的请求。 */
public class SendMessageReq {

    /** 用户输入。 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "单条消息最长 4000 字符")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
