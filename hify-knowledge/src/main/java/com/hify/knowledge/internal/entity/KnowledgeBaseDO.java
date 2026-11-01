package com.hify.knowledge.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.shared.mybatis.BaseEntity;

/**
 * 知识库的数据对象。
 *
 * <p>配置在 MySQL；分段正文与向量在 PostgreSQL 的 {@code vec_chunk}，
 * 两边靠 {@code kb_id} / {@code doc_id} 逻辑关联，跨库无外键。
 */
@TableName(value = "kb_knowledge_base")
public class KnowledgeBaseDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 名称，全局唯一。 */
    @TableField("name")
    private String name;

    /** 描述。 */
    @TableField("description")
    private String description;

    /** 向量化用的模型 {@code ai_model.public_id}。 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 向量维度，必须与 {@code vec_chunk.embedding} 的维度一致。 */
    @TableField("embedding_dim")
    private Integer embeddingDim;

    /** 分块大小，单位字符。 */
    @TableField("chunk_size")
    private Integer chunkSize;

    /** 分块重叠，单位字符。 */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /** 文档数（冗余计数）。 */
    @TableField("doc_count")
    private Integer docCount;

    /** 状态：active / disabled。 */
    @TableField("status")
    private String status;

    /** 知识库配置版本，用于乐观锁。 */
    @TableField("config_version")
    private Long configVersion;

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
}
