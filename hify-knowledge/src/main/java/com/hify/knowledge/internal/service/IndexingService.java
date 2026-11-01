package com.hify.knowledge.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.EmbeddingService;
import com.hify.knowledge.internal.chunk.TextChunker;
import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.internal.repository.ChunkRecord;
import com.hify.knowledge.internal.repository.VecChunkRepository;
import com.hify.shared.config.ThreadPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文档索引：分块 → 向量化 → 写 PG。
 *
 * <p><b>跨库写入必须可重入</b> —— 两库之间没有分布式事务（CLAUDE.md §3.3）：
 * <ul>
 *   <li>PG 侧靠 {@code uk_vec_chunk_doc_idx} 做 upsert，重跑不会产生重复分段</li>
 *   <li>MySQL 侧靠状态机（indexing / ready / failed）表达进度</li>
 *   <li>定时任务兜底重跑卡在 indexing 的文档</li>
 * </ul>
 *
 * <p><b>线程模型</b>：索引任务整体跑在 {@code asyncExecutor}；真正的模型调用
 * 提交到 {@code llmExecutor} 再等结果 —— 这样并发 LLM 调用的天花板（50）
 * 仍然生效，asyncExecutor 的线程只负责等。
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    /** 一次向量化送多少段 —— 段太多会把请求体撑爆。 */
    private static final int EMBED_BATCH_SIZE = 32;

    /** 单批向量化的等待上限。 */
    private static final long EMBED_TIMEOUT_SECONDS = 120L;

    /** 失败原因的最大长度，与 {@code kb_document.error_message} 的列宽一致。 */
    private static final int MAX_ERROR_LENGTH = 1024;

    private final DocumentMapper documents;
    private final KnowledgeBaseMapper knowledgeBases;
    private final VecChunkRepository chunks;
    private final EmbeddingService embeddings;
    private final TextChunker chunker;
    private final Executor asyncExecutor;
    private final ExecutorService llmExecutor;

    public IndexingService(DocumentMapper documents, KnowledgeBaseMapper knowledgeBases,
                           VecChunkRepository chunks, EmbeddingService embeddings, TextChunker chunker,
                           @Qualifier(ThreadPoolConfig.ASYNC_EXECUTOR) Executor asyncExecutor,
                           @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ExecutorService llmExecutor) {
        this.documents = documents;
        this.knowledgeBases = knowledgeBases;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.chunker = chunker;
        this.asyncExecutor = asyncExecutor;
        this.llmExecutor = llmExecutor;
    }

    /**
     * 提交一次索引任务。
     *
     * @param documentId 文档主键
     */
    public void submit(Long documentId) {
        try {
            asyncExecutor.execute(() -> index(documentId));
        } catch (RejectedExecutionException exception) {
            log.warn("索引任务队列已满，文档暂不索引 documentId={}", documentId);
            markFailed(documentId, "索引任务队列已满，请稍后重试");
        }
    }

    /**
     * 同步执行一次索引 —— 定时兜底任务直接调用。
     *
     * @param documentId 文档主键
     */
    public void indexNow(Long documentId) {
        index(documentId);
    }

    private void index(Long documentId) {
        DocumentDO document = documents.selectById(documentId);
        if (document == null || !Integer.valueOf(0).equals(document.getDeleted())) {
            return;
        }
        KnowledgeBaseDO knowledgeBase = knowledgeBases.selectById(document.getKbId());
        if (knowledgeBase == null) {
            markFailed(documentId, "所属知识库不存在");
            return;
        }
        markIndexing(documentId);
        try {
            List<String> pieces = chunker.split(document.getContent(),
                    knowledgeBase.getChunkSize(), knowledgeBase.getChunkOverlap());
            if (pieces.isEmpty()) {
                chunks.deleteByDocId(documentId);
                markReady(documentId, 0);
                return;
            }
            // 重新索引时先清掉旧分段：段数可能变少，留着会残留过期内容
            chunks.deleteByDocId(documentId);

            List<float[]> vectors = new ArrayList<>(pieces.size());
            for (int from = 0; from < pieces.size(); from += EMBED_BATCH_SIZE) {
                List<String> batch = pieces.subList(from, Math.min(pieces.size(), from + EMBED_BATCH_SIZE));
                vectors.addAll(embed(knowledgeBase.getEmbeddingModel(), batch));
            }
            for (int index = 0; index < pieces.size(); index++) {
                chunks.upsertChunks(knowledgeBase.getId(), documentId,
                        List.of(new ChunkRecord(index, pieces.get(index), vectors.get(index))));
            }
            markReady(documentId, pieces.size());
            log.info("文档索引完成 documentId={} chunks={}", documentId, pieces.size());
        } catch (RuntimeException exception) {
            log.warn("文档索引失败 documentId={} reason={}", documentId, exception.getClass().getSimpleName());
            markFailed(documentId, describe(exception));
        }
    }

    /**
     * 调用向量化服务。
     *
     * <p>提交到 {@code llmExecutor} 再等结果 —— 并发模型调用的天花板在这里生效。
     */
    private List<float[]> embed(String modelPublicId, List<String> batch) {
        try {
            Future<List<float[]>> future = llmExecutor.submit(() -> embeddings.embed(modelPublicId, batch));
            return future.get(EMBED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("向量化被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("向量化失败", cause);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("向量化超时", exception);
        } catch (RejectedExecutionException exception) {
            throw new IllegalStateException("向量化线程池繁忙", exception);
        }
    }

    private void markIndexing(Long documentId) {
        DocumentDO update = new DocumentDO();
        update.setId(documentId);
        update.setStatus(KnowledgeSupport.DOC_INDEXING);
        update.setErrorMessage(null);
        documents.updateById(update);
    }

    private void markReady(Long documentId, int chunkCount) {
        DocumentDO update = new DocumentDO();
        update.setId(documentId);
        update.setStatus(KnowledgeSupport.DOC_READY);
        update.setChunkCount(chunkCount);
        update.setErrorMessage(null);
        update.setIndexedAt(LocalDateTime.now(ZoneOffset.UTC));
        documents.updateById(update);
        refreshDocCount(documentId);
    }

    private void markFailed(Long documentId, String message) {
        DocumentDO update = new DocumentDO();
        update.setId(documentId);
        update.setStatus(KnowledgeSupport.DOC_FAILED);
        update.setErrorMessage(truncate(message));
        documents.updateById(update);
    }

    /** 文档状态变了，知识库上的冗余文档数也要跟着变。 */
    private void refreshDocCount(Long documentId) {
        DocumentDO document = documents.selectById(documentId);
        if (document == null) {
            return;
        }
        KnowledgeBaseDO knowledgeBase = knowledgeBases.selectById(document.getKbId());
        if (knowledgeBase == null) {
            return;
        }
        KnowledgeBaseDO update = new KnowledgeBaseDO();
        update.setId(knowledgeBase.getId());
        update.setDocCount((int) documents.countByKbId(knowledgeBase.getId()));
        knowledgeBases.updateById(update);
    }

    /** 失败原因用固定文案，不把上游响应体写进库。 */
    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "索引失败";
        }
        return "索引失败：" + message;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    /**
     * 该索引却没索引的文档 —— 定时兜底任务用。
     *
     * <p>两种状态都算：{@code uploaded}（任务根本没被提交或提交后丢了）与
     * {@code indexing}（任务开始了但中途挂了）。只捞 indexing 会让前者永远停在
     * uploaded —— 症状是「文档传上去了，但检索不到任何内容」。
     */
    public List<DocumentDO> listPendingIndexing(LocalDateTime before, int limit) {
        return documents.selectList(new QueryWrapper<DocumentDO>()
                .in("status", List.of(KnowledgeSupport.DOC_UPLOADED, KnowledgeSupport.DOC_INDEXING))
                .lt("updated_at", before)
                .last("LIMIT " + limit));
    }

    /** 处于 deleting 的文档 —— 定时兜底任务用。 */
    public List<DocumentDO> listDeleting(int limit) {
        return documents.selectList(new QueryWrapper<DocumentDO>()
                .eq("status", KnowledgeSupport.DOC_DELETING)
                .last("LIMIT " + limit));
    }
}
