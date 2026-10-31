package com.hify.ai.api;

import java.time.LocalDateTime;

/** 不含凭据或健康快照的接入点摘要。 */
public class ProviderSummaryResp {

    /** 内部主键，供模块间关联使用。 */
    private Long id;

    /** 对外接入点标识。 */
    private String publicId;

    /** 名称。 */
    private String name;

    /** 协议。 */
    private String type;

    /** 协议根地址。 */
    private String baseUrl;

    /** 启用标记。 */
    private Boolean enabled;

    /** 当前配置版本。 */
    private Long configVersion;

    /** 是否配置了密钥，不返回密钥或密文。 */
    private Boolean credentialsConfigured;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 配置更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

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

    public Boolean getCredentialsConfigured() {
        return credentialsConfigured;
    }

    public void setCredentialsConfigured(Boolean credentialsConfigured) {
        this.credentialsConfigured = credentialsConfigured;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
