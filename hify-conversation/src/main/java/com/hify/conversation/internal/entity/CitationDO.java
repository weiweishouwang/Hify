package com.hify.conversation.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消息引用的数据对象。
 *
 * <p><b>不继承 {@link com.hify.shared.mybatis.BaseEntity}</b>：事件流表，
 * 主键是 {@code (id, created_at)} 复合主键，也没有 {@code deleted} 列。
 *
 * <p>引用文本存快照：知识库重新索引后 chunk id 会变，
 * 历史对话的溯源不能依赖 PG 还在（CLAUDE.md §3.3）。
 */
@TableName("conv_message_citation")
public class CitationDO {

    /** 自增主键；与 created_at 组成复合主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 conv_session.id。 */
    @TableField("session_id")
    private Long sessionId;

    /** 所属消息 conv_message.id。 */
    @TableField("message_id")
    private Long messageId;

    /** 引用的分段 vec_chunk.id。 */
    @TableField("chunk_id")
    private Long chunkId;

    /** 冗余：文档 kb_document.id。 */
    @TableField("doc_id")
    private Long docId;

    /** 冗余：分段序号。 */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /** 冗余：所属知识库 kb_knowledge_base.id。 */
    @TableField("kb_id")
    private Long kbId;

    /** 引用文本快照。 */
    @TableField("quote_text")
    private String quoteText;

    /** 相似度得分。 */
    @TableField("score")
    private BigDecimal score;

    /** 创建时间(UTC) —— 分区键。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getQuoteText() {
        return quoteText;
    }

    public void setQuoteText(String quoteText) {
        this.quoteText = quoteText;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
