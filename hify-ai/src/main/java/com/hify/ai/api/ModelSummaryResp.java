package com.hify.ai.api;

import java.util.Map;

/** 模型摘要：不含凭据，也不含上游扩展参数的敏感项。 */
public class ModelSummaryResp {

    /** 内部主键，供模块间关联使用。 */
    private Long id;

    /** 对外模型标识。 */
    private String publicId;

    /** 所属接入点的对外标识。 */
    private String providerPublicId;

    /** 所属接入点名称，仅用于展示。 */
    private String providerName;

    /** 上游实际接受的模型标识，原样发送。 */
    private String modelName;

    /** 管理台展示名称。 */
    private String displayName;

    /** 用途：chat / embedding。 */
    private String modelType;

    /** 声明的能力，如 stream / tool_call / vision。 */
    private Map<String, Object> capabilities;

    /** 上下文窗口上限，单位 token；未知时为 null。 */
    private Integer contextWindow;

    /** 单次输出上限，单位 token；未知时为 null。 */
    private Integer maxOutputTokens;

    /** embedding 向量维度；非 embedding 模型或尚未确认时为 null。 */
    private Integer embeddingDimension;

    /** 是否启用。 */
    private Boolean enabled;

    /** 展示顺序，小值在前。 */
    private Integer sortOrder;

    /** 模型配置版本，用于乐观锁。 */
    private Long configVersion;

    /** 创建时间(UTC)。 */
    private java.time.LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private java.time.LocalDateTime updatedAt;

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

    public String getProviderPublicId() {
        return providerPublicId;
    }

    public void setProviderPublicId(String providerPublicId) {
        this.providerPublicId = providerPublicId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Map<String, Object> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, Object> capabilities) {
        this.capabilities = capabilities;
    }

    public Integer getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(Integer contextWindow) {
        this.contextWindow = contextWindow;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
