package com.hify.conversation.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 更新会话的请求：改标题、归档或取消归档。 */
public class ConversationUpdateReq {

    /** 会话标题。 */
    @Size(max = 255, message = "标题最长 255 字符")
    private String title;

    /** 状态：active / archived。 */
    @Pattern(regexp = "active|archived", message = "不支持的会话状态")
    private String status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
