package com.hify.ai.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;

import java.util.Map;

/** 模型的数据对象。同一个上游模型标识可以分别挂在不同接入点下。 */
@TableName(value = "ai_model", autoResultMap = true)
public class ModelDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 所属接入点 ai_model_provider.id。 */
    @TableField("provider_id")
    private Long providerId;

    /** 上游实际接受的模型标识，原样发送。 */
    @TableField("model_name")
    private String modelName;

    /** 管理台展示名称。 */
    @TableField("display_name")
    private String displayName;

    /** 模型用途：chat / embedding，rerank 预留。 */
    @TableField("model_type")
    private String modelType;

    /** 声明的模型能力，如 stream / tool_call / vision；声明不等于检查通过。 */
    @TableField(value = "capabilities", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> capabilities;

    /** 模型上下文窗口上限，单位 token；未知时为 null。 */
    @TableField("context_window")
    private Integer contextWindow;

    /** 模型单次输出上限，单位 token；未知时为 null。 */
    @TableField("max_output_tokens")
    private Integer maxOutputTokens;

    /** embedding 向量维度；非 embedding 模型或尚未确认时为 null。 */
    @TableField("embedding_dimension")
    private Integer embeddingDimension;

    /** 通用默认生成参数，由调用层校验后使用。 */
    @TableField(value = "default_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> defaultParams;

    /** 模型级的上游扩展参数，不存放凭据。 */
    @TableField(value = "extra_params", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    /** 是否允许使用此模型；接入点也必须处于启用状态。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 展示顺序，小值在前。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 影响调用的模型配置版本，由业务层递增。 */
    @TableField("config_version")
    private Long configVersion;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
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
