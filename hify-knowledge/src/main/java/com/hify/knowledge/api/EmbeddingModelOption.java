package com.hify.knowledge.api;

/**
 * 可选 embedding 模型的选项。
 *
 * <p>建知识库时要选一个向量模型 —— 只能从<b>已启用且已确认维度</b>的 embedding 模型里选，
 * 否则建出来的库必然写不进向量。
 */
public class EmbeddingModelOption {

    /** 模型的对外标识，建库时提交这个。 */
    private String publicId;

    /** 展示名。 */
    private String displayName;

    /** 上游模型标识。 */
    private String modelName;

    /** 所属接入点名称，仅用于展示。 */
    private String providerName;

    /** 向量维度。 */
    private Integer embeddingDimension;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }
}
