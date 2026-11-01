package com.hify.knowledge.api;

import java.time.LocalDateTime;

/** 文档摘要。 */
public class DocumentSummaryResp {

    /** 对外文档标识。 */
    private String publicId;

    /** 所属知识库的对外标识。 */
    private String kbPublicId;

    /** 文件名。 */
    private String name;

    /** 文件类型，一期只支持 txt。 */
    private String fileType;

    /** 字节数。 */
    private Long fileSize;

    /** 内容 SHA-256，用于去重。 */
    private String contentHash;

    /** 状态机：uploaded / indexing / ready / failed / deleting。 */
    private String status;

    /** 分段数。 */
    private Integer chunkCount;

    /** 失败原因。 */
    private String errorMessage;

    /** 索引完成时间(UTC)。 */
    private LocalDateTime indexedAt;

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

    public String getKbPublicId() {
        return kbPublicId;
    }

    public void setKbPublicId(String kbPublicId) {
        this.kbPublicId = kbPublicId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
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
