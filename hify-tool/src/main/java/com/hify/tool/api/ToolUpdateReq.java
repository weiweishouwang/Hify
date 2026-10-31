package com.hify.tool.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 更新工具的请求。
 *
 * <p><b>请求头是完整替换，不是增量</b>：
 * <ul>
 *   <li>非敏感头：提交什么就是什么，没提交的键被删除</li>
 *   <li>敏感头：提交明文 = 替换；提交 {@code ******} = 保留原值；没提交该键 = 删除</li>
 *   <li>值为空串的键一律不写入</li>
 * </ul>
 * 详情接口回显的敏感头就是 {@code ******}，前端原样回传即表示「不动它」。
 *
 * <p>{@code sourceType} 与 {@code toolKey} 不可改。
 */
public class ToolUpdateReq {

    /** 工具名：进 function calling 的 function name，全局唯一。 */
    @Size(max = 128, message = "工具名最长 128 字符")
    private String name;

    /** 工具描述，给模型看。 */
    @Size(max = 2000, message = "工具描述最长 2000 字符")
    private String description;

    /** 入参 JSON Schema，给模型看。 */
    private Map<String, Object> schema;

    /** 请求地址。 */
    @Size(max = 512, message = "请求地址最长 512 字符")
    private String url;

    /** 请求方法，一期只支持 GET 与 POST。 */
    @jakarta.validation.constraints.Pattern(regexp = "GET|POST", message = "请求方法只支持 GET 或 POST")
    private String method;

    /** 请求头；完整替换，规则见类注释。 */
    private Map<String, String> headers;

    /** 超时毫秒数；不传表示不改动。 */
    @Min(value = 1000, message = "超时不能小于 1000 毫秒")
    @Max(value = 120000, message = "超时不能大于 120000 毫秒")
    private Integer timeoutMs;

    /** 是否启用；不传表示不改动。 */
    private Boolean enabled;

    /** 工具配置版本，用于乐观锁；不匹配则拒绝写入。 */
    @NotNull(message = "缺少工具配置版本")
    @Positive(message = "工具配置版本必须为正数")
    private Long configVersion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }
}
