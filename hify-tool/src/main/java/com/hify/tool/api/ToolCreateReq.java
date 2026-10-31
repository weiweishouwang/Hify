package com.hify.tool.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 创建自定义 HTTP 工具的请求。 */
public class ToolCreateReq {

    /** 工具名：进 function calling 的 function name，全局唯一。 */
    @NotBlank(message = "工具名不能为空")
    @Size(max = 128, message = "工具名最长 128 字符")
    private String name;

    /** 来源：一期只接受手工创建 http 来源的工具，其余来源由同步机制写入。 */
    @NotBlank(message = "工具来源不能为空")
    @Pattern(regexp = "http", message = "当前只支持手工创建 http 来源的工具")
    private String sourceType;

    /** 来源内唯一的工具标识。 */
    @NotBlank(message = "工具标识不能为空")
    @Size(max = 128, message = "工具标识最长 128 字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "工具标识只能包含字母、数字、下划线和连字符")
    private String toolKey;

    /** 工具描述，给模型看。 */
    @Size(max = 2000, message = "工具描述最长 2000 字符")
    private String description;

    /** 入参 JSON Schema，给模型看。 */
    private Map<String, Object> schema;

    /** 请求地址。 */
    @NotBlank(message = "请求地址不能为空")
    @Size(max = 512, message = "请求地址最长 512 字符")
    private String url;

    /** 请求方法，一期只支持 GET 与 POST。 */
    @Pattern(regexp = "GET|POST", message = "请求方法只支持 GET 或 POST")
    private String method;

    /** 请求头；敏感头的值落库前会被加密。 */
    private Map<String, String> headers;

    /** 超时毫秒数；不传用默认 30000，上限 120000。 */
    @Min(value = 1000, message = "超时不能小于 1000 毫秒")
    @Max(value = 120000, message = "超时不能大于 120000 毫秒")
    private Integer timeoutMs;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getToolKey() {
        return toolKey;
    }

    public void setToolKey(String toolKey) {
        this.toolKey = toolKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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
