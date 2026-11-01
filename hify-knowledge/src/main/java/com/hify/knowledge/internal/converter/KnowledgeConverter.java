package com.hify.knowledge.internal.converter;

import com.hify.knowledge.api.DocumentSummaryResp;
import com.hify.knowledge.api.EmbeddingModelOption;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import org.springframework.stereotype.Component;

/** 知识库与文档的数据对象到对外 DTO 的转换。 */
@Component
public class KnowledgeConverter {

    /** 知识库 DO → 摘要。 */
    public KnowledgeBaseSummaryResp toSummary(KnowledgeBaseDO row) {
        KnowledgeBaseSummaryResp resp = new KnowledgeBaseSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setDescription(row.getDescription());
        resp.setEmbeddingModel(row.getEmbeddingModel());
        resp.setEmbeddingDim(row.getEmbeddingDim());
        resp.setChunkSize(row.getChunkSize());
        resp.setChunkOverlap(row.getChunkOverlap());
        resp.setDocCount(row.getDocCount());
        resp.setStatus(row.getStatus());
        resp.setConfigVersion(row.getConfigVersion());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /**
     * 文档 DO → 摘要。
     *
     * @param row         文档行
     * @param kbPublicId 所属知识库的对外标识
     */
    public DocumentSummaryResp toSummary(DocumentDO row, String kbPublicId) {
        DocumentSummaryResp resp = new DocumentSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setKbPublicId(kbPublicId);
        resp.setName(row.getName());
        resp.setFileType(row.getFileType());
        resp.setFileSize(row.getFileSize());
        resp.setContentHash(row.getContentHash());
        resp.setStatus(row.getStatus());
        resp.setChunkCount(row.getChunkCount());
        resp.setErrorMessage(row.getErrorMessage());
        resp.setIndexedAt(row.getIndexedAt());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /** ai 模块的模型摘要 → embedding 模型选项。 */
    public EmbeddingModelOption toModelOption(com.hify.ai.api.ModelSummaryResp model) {
        EmbeddingModelOption option = new EmbeddingModelOption();
        option.setPublicId(model.getPublicId());
        option.setDisplayName(model.getDisplayName());
        option.setModelName(model.getModelName());
        option.setProviderName(model.getProviderName());
        option.setEmbeddingDimension(model.getEmbeddingDimension());
        return option;
    }
}
