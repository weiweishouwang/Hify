package com.hify.knowledge.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.shared.mybatis.BaseEntity;

import java.time.LocalDateTime;

/**
 * 文档的数据对象：元数据与状态机在 MySQL。
 *
 * <p>分段正文与向量在 PostgreSQL 的 {@code vec_chunk}；{@code content} 存的是
 * 文档原文 —— 没有它，向量算不出来、分段正文也无处可放。
 */
@TableName(value = "kb_document")
public class DocumentDO extends BaseEntity {

    /** 对外 ID，ULID。 */
    @TableField("public_id")
    private String publicId;

    /** 所属知识库 {@code kb_knowledge_base.id}（同库，有外键）。 */
    @TableField("kb_id")
    private Long kbId;

    /** 文件名。 */
    @TableField("name")
    private String name;

    /** 文件类型，一期只支持 txt。 */
    @TableField("file_type")
    private String fileType;

    /** 字节数。 */
    @TableField("file_size")
    private Long fileSize;

    /** 内容 SHA-256，用于去重。 */
    @TableField("content_hash")
    private String contentHash;

    /** 文档原文，一期仅 TXT。 */
    @TableField("content")
    private String content;

    /** 状态机：uploaded / indexing / ready / failed / deleting。 */
    @TableField("status")
    private String status;

    /** 分段数。 */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 失败原因。 */
    @TableField("error_message")
    private String errorMessage;

    /** 索引完成时间(UTC)。 */
    @TableField("indexed_at")
    private LocalDateTime indexedAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
}
