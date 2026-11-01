package com.hify.knowledge.api;

import java.time.LocalDateTime;

/** 知识库摘要。 */
public class KnowledgeBaseSummaryResp {

    /** 对外知识库标识。 */
    private String publicId;

    /** 名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 向量化用的模型 ai_model.public_id。 */
    private String embeddingModel;

    /** 向量维度，必须与 vec_chunk 的维度一致。 */
    private Integer embeddingDim;

    /** 分块大小，单位字符。 */
    private Integer chunkSize;

    /** 分块重叠，单位字符。 */
    private Integer chunkOverlap;

    /** 文档数（冗余计数）。 */
    private Integer docCount;

    /** 状态：active / disabled。 */
    private String status;

    /** 知识库配置版本，用于乐观锁。 */
    private Long configVersion;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private LocalDateTime updatedAt;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(Integer embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public Integer getDocCount() {
        return docCount;
    }

    public void setDocCount(Integer docCount) {
        this.docCount = docCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
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
