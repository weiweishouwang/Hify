package com.hify.ai.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 更新模型配置的请求。
 *
 * <p>上游模型标识 {@code model_name} 与所属接入点创建后不可改 —— 改了会导致
 * 已保存的调用参数对不上上游。
 */
public class ModelUpdateReq {

    /** 展示名称。 */
    @Size(max = 128, message = "展示名最长 128 字符")
    private String displayName;

    /** 用途：chat / embedding。上游目录接口不标类型，由这里人工认定。 */
    @Pattern(regexp = "chat|embedding", message = "模型用途只支持 chat 或 embedding")
    private String modelType;

    /** 声明的能力，如 stream / tool_call / vision。 */
    private Map<String, Object> capabilities;

    /** 上下文窗口上限，单位 token。 */
    @Min(value = 1, message = "上下文窗口必须为正数")
    private Integer contextWindow;

    /** 单次输出上限，单位 token。 */
    @Min(value = 1, message = "输出上限必须为正数")
    private Integer maxOutputTokens;

    /** embedding 向量维度；知识库建库时要用它校验与 vec_chunk 的维度一致。 */
    @Min(value = 1, message = "向量维度必须为正数")
    @Max(value = 16384, message = "向量维度过大")
    private Integer embeddingDimension;

    /** 是否启用；不传表示不改动。 */
    private Boolean enabled;

    /** 展示顺序。 */
    @Min(value = 0, message = "排序值不能为负数")
    private Integer sortOrder;

    /** 模型配置版本，用于乐观锁；不匹配则拒绝写入。 */
    @NotNull(message = "缺少模型配置版本")
    @Positive(message = "模型配置版本必须为正数")
    private Long configVersion;

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
}
