package com.hify.tool.api;

import java.util.Map;

/** 手工测试一个工具的结果。 */
public class ToolInvokeResp {

    /** 是否执行成功。 */
    private Boolean success;

    /** 成功时上游返回的内容。 */
    private String content;

    /** 失败时的可读原因。 */
    private String errorMessage;

    /** 附加信息，例如耗时、是否截断。 */
    private Map<String, Object> metadata;

    /** 执行耗时(毫秒)。 */
    private Long durationMs;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
