package com.hify.ai.api;

import java.util.Map;

/** 接入点下的模型配置，不包含数据库映射对象。 */
public class ModelConfigResp {

    /** 模型对外标识。 */
    private String publicId;

    /** 上游模型标识。 */
    private String modelName;

    /** 展示名称。 */
    private String displayName;

    /** 模型用途。 */
    private String modelType;

    /** 声明的模型能力。 */
    private Map<String, Object> capabilities;

    /** 上下文窗口 token 数。 */
    private Integer contextWindow;

    /** 单次输出 token 上限。 */
    private Integer maxOutputTokens;

    /** 向量维度。 */
    private Integer embeddingDimension;

    /** 通用默认参数。 */
    private Map<String, Object> defaultParams;

    /** 模型级扩展参数。 */
    private Map<String, Object> extraParams;

    /** 是否启用。 */
    private Boolean enabled;

    /** 排序值。 */
    private Integer sortOrder;

    /** 模型配置版本。 */
    private Long configVersion;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
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

    public Map<String, Object> getDefaultParams() {
        return defaultParams;
    }

    public void setDefaultParams(Map<String, Object> defaultParams) {
        this.defaultParams = defaultParams;
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

}
