package com.hify.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** 创建模型接入点的请求；密钥只允许写入，不在响应中回传。 */
public class ProviderCreateReq {
    public static final String PROTOCOL_PATTERN = "openai|anthropic|ollama";

    /** 接入点名称。 */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** 一期协议：openai / anthropic / ollama。 */
    @NotBlank
    @Pattern(regexp = PROTOCOL_PATTERN, message = "type 必须为 openai、anthropic 或 ollama")
    private String type;

    /** 协议根地址。 */
    @NotBlank
    @Size(max = 512)
    private String baseUrl;

    /** 按协议定义的鉴权配置，apiKey 为待加密的字符串。 */
    private Map<String, Object> authConfig;

    /** 非敏感的上游扩展参数。 */
    private Map<String, Object> extraParams;

    /** 是否启用；不传时默认启用。 */
    private Boolean enabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(Map<String, Object> authConfig) {
        this.authConfig = authConfig;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}
