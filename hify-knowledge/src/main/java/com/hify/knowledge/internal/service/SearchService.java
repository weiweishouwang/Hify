package com.hify.knowledge.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.EmbeddingService;
import com.hify.knowledge.api.KnowledgeSearchService;
import com.hify.knowledge.api.SearchQuery;
import com.hify.knowledge.api.SearchResult;
import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.internal.repository.ChunkHit;
import com.hify.knowledge.internal.repository.VecChunkRepository;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库的向量检索。
 *
 * <p><b>纯能力层</b>：只做「给我查询文本，返回相关分段」。不拼上下文、
 * 不调模型回答 —— 那是编排层的事（CLAUDE.md §2.1 的模块不变量）。
 *
 * <p>调用是阻塞的（要等上游向量化 + PG 检索），HTTP 入口应提交到
 * {@code llmExecutor}。
 */
@Service
public class SearchService implements KnowledgeSearchService {

    private final KnowledgeBaseMapper knowledgeBases;
    private final DocumentMapper documents;
    private final VecChunkRepository chunks;
    private final EmbeddingService embeddings;

    public SearchService(KnowledgeBaseMapper knowledgeBases, DocumentMapper documents,
                          VecChunkRepository chunks, EmbeddingService embeddings) {
        this.knowledgeBases = knowledgeBases;
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
    }

    @Override
    public List<SearchResult> search(String kbPublicId, SearchQuery query) {
        if (query == null || query.getQuery() == null || query.getQuery().isBlank()) {
            throw KnowledgeSupport.invalid("检索内容不能为空");
        }
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(kbPublicId);
        if (!KnowledgeSupport.KB_ACTIVE.equals(knowledgeBase.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "知识库已停用");
        }
        int topK = KnowledgeSupport.topK(query.getTopK());
        List<float[]> vectors = embeddings.embed(knowledgeBase.getEmbeddingModel(), List.of(query.getQuery()));
        if (vectors.isEmpty()) {
            return List.of();
        }
        List<ChunkHit> hits = chunks.search(knowledgeBase.getId(), vectors.get(0), topK);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, String> docNames = loadDocNames(hits);
        List<SearchResult> results = new ArrayList<>(hits.size());
        for (ChunkHit hit : hits) {
            results.add(new SearchResult(hit.id(), hit.docId(), docNames.get(hit.docId()),
                    hit.chunkIndex(), hit.content(), hit.score()));
        }
        return results;
    }

    /** 批量取文档名，便于溯源。 */
    private Map<Long, String> loadDocNames(List<ChunkHit> hits) {
        Set<Long> docIds = hits.stream().map(ChunkHit::docId).collect(java.util.stream.Collectors.toSet());
        if (docIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (DocumentDO document : documents.selectBatchIds(docIds)) {
            names.put(document.getId(), document.getName());
        }
        return names;
    }

    private KnowledgeBaseDO requireKnowledgeBase(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw KnowledgeSupport.invalid("知识库标识不能为空");
        }
        KnowledgeBaseDO entity = knowledgeBases.selectOne(new QueryWrapper<KnowledgeBaseDO>()
                .eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return entity;
    }
}
