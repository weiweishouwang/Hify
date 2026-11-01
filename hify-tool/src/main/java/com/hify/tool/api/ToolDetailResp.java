package com.hify.tool.api;

import java.util.Map;

/**
 * 工具详情。
 *
 * <p>请求头里的敏感值（Authorization 这类）以 {@code ******} 占位返回，
 * <b>密文与明文都不给</b>。编辑时留空即表示保留原值。
 */
public class ToolDetailResp extends ToolSummaryResp {

    /**
     * 敏感请求头的占位值。
     *
     * <p>详情接口用它替换请求头里的敏感值；编辑时原样回传即表示「保留原值不改」。
     */
    public static final String MASKED_VALUE = com.hify.shared.crypto.SensitiveHeaderCipher.MASKED_VALUE;

    /** 入参 JSON Schema，给模型看。 */
    private Map<String, Object> schema;

    /** 请求地址，仅 http 来源有值。 */
    private String url;

    /** 请求方法，仅 http 来源有值。 */
    private String method;

    /** 请求头；敏感值以 {@code ******} 占位。 */
    private Map<String, String> headers;

    /** 超时毫秒数，仅 http 来源有值。 */
    private Integer timeoutMs;

    public Map<String, Object> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, Object> schema) {
        this.schema = schema;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
