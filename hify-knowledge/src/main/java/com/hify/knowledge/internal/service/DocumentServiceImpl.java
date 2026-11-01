package com.hify.knowledge.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.knowledge.api.DocumentListQuery;
import com.hify.knowledge.api.DocumentRemovedEvent;
import com.hify.knowledge.api.DocumentUploadedEvent;
import com.hify.knowledge.api.DocumentListResp;
import com.hify.knowledge.api.DocumentSummaryResp;
import com.hify.knowledge.api.DocumentUpload;
import com.hify.knowledge.api.KnowledgeDocumentService;
import com.hify.knowledge.internal.converter.KnowledgeConverter;
import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.internal.repository.VecChunkRepository;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.id.IdGenerator;
import com.hify.shared.mybatis.CursorCodec;
import com.hify.shared.web.PageQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 文档的上传、列表与删除。
 *
 * <p><b>删除顺序固定「MySQL 软删 → 异步删 PG → MySQL 硬删」</b>
 * （CLAUDE.md §3.3）：反过来会在中途崩溃时让检索静默返回空 —— PG 已清、
 * MySQL 还显示 ready，但检索时一条分段都查不到。
 */
@Service
public class DocumentServiceImpl implements KnowledgeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final DocumentMapper documents;
    private final KnowledgeBaseMapper knowledgeBases;
    private final VecChunkRepository chunks;
    private final KnowledgeConverter converter;
    private final ApplicationEventPublisher events;
    private final Executor asyncExecutor;

    public DocumentServiceImpl(DocumentMapper documents, KnowledgeBaseMapper knowledgeBases,
                               VecChunkRepository chunks,
                               KnowledgeConverter converter, ApplicationEventPublisher events,
                               @Qualifier(ThreadPoolConfig.ASYNC_EXECUTOR) Executor asyncExecutor) {
        this.documents = documents;
        this.knowledgeBases = knowledgeBases;
        this.chunks = chunks;
        this.converter = converter;
        this.events = events;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummaryResp upload(String kbPublicId, DocumentUpload upload) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(kbPublicId);
        if (upload == null) {
            throw KnowledgeSupport.invalid("请选择要上传的文件");
        }
        if (!upload.isTxt()) {
            throw KnowledgeSupport.invalid("一期只支持 TXT 文件");
        }
        if (upload.content().length > KnowledgeSupport.MAX_UPLOAD_BYTES) {
            throw KnowledgeSupport.invalid("文件超过 10 MB 上限");
        }
        if (upload.isEmpty()) {
            throw KnowledgeSupport.invalid("文件内容为空");
        }
        String text = upload.text();
        if (text.isBlank()) {
            throw KnowledgeSupport.invalid("文件内容为空");
        }

        DocumentDO entity = new DocumentDO();
        entity.setPublicId(IdGenerator.nextUlid());
        entity.setKbId(knowledgeBase.getId());
        entity.setName(upload.displayName());
        entity.setFileType(KnowledgeSupport.FILE_TYPE_TXT);
        entity.setFileSize((long) upload.content().length);
        entity.setContentHash(KnowledgeSupport.contentHash(upload.content()));
        entity.setContent(text);
        entity.setStatus(KnowledgeSupport.DOC_UPLOADED);
        entity.setChunkCount(0);
        documents.insert(entity);

        // 索引是慢能力，异步做；接口不等它。
        // 事件在事务提交后才被监听 —— 索引任务要立刻回查文档行，事务没提交时它还看不见。
        events.publishEvent(new DocumentUploadedEvent(entity.getId()));
        return converter.toSummary(entity, knowledgeBase.getPublicId());
    }

    @Override
    public DocumentListResp list(String kbPublicId, DocumentListQuery query) {
        KnowledgeBaseDO knowledgeBase = requireKnowledgeBase(kbPublicId);
        DocumentListQuery effective = query == null ? new DocumentListQuery() : query;
        var wrapper = new QueryWrapper<DocumentDO>().eq("kb_id", knowledgeBase.getId());
        if (effective.getStatus() != null && !effective.getStatus().isBlank()) {
            wrapper.eq("status", effective.getStatus());
        }
        if (effective.getCursor() != null && !effective.getCursor().isBlank()) {
            var cursor = CursorCodec.decode(effective.getCursor());
            wrapper.apply("(created_at, id) < ({0}, {1})", cursor.createdAt(), cursor.id());
        }
        int size = size(effective);
        wrapper.orderByDesc("created_at", "id").last("LIMIT " + (size + 1));

        List<DocumentDO> rows = documents.selectList(wrapper);
        boolean hasNext = rows.size() > size;
        List<DocumentDO> visible = rows.subList(0, Math.min(rows.size(), size));
        List<DocumentSummaryResp> items = new ArrayList<>();
        visible.forEach(row -> items.add(converter.toSummary(row, knowledgeBase.getPublicId())));

        DocumentListResp result = new DocumentListResp();
        result.setItems(items);
        result.setHasNext(hasNext);
        if (hasNext) {
            var last = visible.getLast();
            result.setNextCursor(CursorCodec.encode(last.getCreatedAt(), last.getId()));
        }
        return result;
    }

    @Override
    public DocumentSummaryResp get(String publicId) {
        DocumentDO document = requireExisting(publicId);
        KnowledgeBaseDO knowledgeBase = knowledgeBases.selectById(document.getKbId());
        return converter.toSummary(document, knowledgeBase == null ? null : knowledgeBase.getPublicId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String publicId) {
        DocumentDO document = requireExisting(publicId);
        DocumentDO update = new DocumentDO();
        update.setId(document.getId());
        update.setStatus(KnowledgeSupport.DOC_DELETING);
        update.setErrorMessage(null);
        documents.updateById(update);
        // 逻辑删除由 @TableLogic 处理；这里把删除标记打上，PG 清完再物理删行。
        documents.deleteById(document.getId());
        // 清理任务同样要等事务提交后才动，否则它会看不到刚打的软删标记。
        events.publishEvent(new DocumentRemovedEvent(document.getId(), document.getKbId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummaryResp reindex(String publicId) {
        DocumentDO document = requireExisting(publicId);
        DocumentDO update = new DocumentDO();
        update.setId(document.getId());
        update.setStatus(KnowledgeSupport.DOC_UPLOADED);
        update.setErrorMessage(null);
        update.setChunkCount(0);
        documents.updateById(update);
        events.publishEvent(new DocumentUploadedEvent(document.getId()));
        return converter.toSummary(update, null);
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    /** 清掉 PG 里的分段，然后物理删掉 MySQL 行。 */
    public void scheduleCleanup(Long documentId, Long kbId) {
        try {
            asyncExecutor.execute(() -> {
                try {
                    chunks.deleteByDocId(documentId);
                    documents.hardDelete(documentId);
                    refreshDocCount(kbId);
                    log.info("文档已彻底删除 documentId={}", documentId);
                } catch (RuntimeException exception) {
                    // 清不掉就留着软删行，等定时兜底任务重来
                    log.warn("文档清理未完成，留待兜底任务重试 documentId={} reason={}",
                            documentId, exception.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("清理任务队列已满，文档留待兜底任务处理 documentId={}", documentId);
        }
    }

    /** 定时兜底任务用：继续删 PG，再硬删 MySQL 行。 */
    public void finishCleanup(Long documentId, Long kbId) {
        chunks.deleteByDocId(documentId);
        documents.hardDelete(documentId);
        refreshDocCount(kbId);
    }

    /** 知识库上的冗余文档数。 */
    public void refreshDocCount(Long kbId) {
        KnowledgeBaseDO knowledgeBase = knowledgeBases.selectById(kbId);
        if (knowledgeBase == null) {
            return;
        }
        KnowledgeBaseDO update = new KnowledgeBaseDO();
        update.setId(kbId);
        update.setDocCount((int) documents.countByKbId(kbId));
        knowledgeBases.updateById(update);
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

    private DocumentDO requireExisting(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw KnowledgeSupport.invalid("文档标识不能为空");
        }
        DocumentDO entity = documents.selectOne(new QueryWrapper<DocumentDO>().eq("public_id", publicId));
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return entity;
    }

    private static int size(DocumentListQuery query) {
        if (query.getSize() == null) {
            return PageQuery.DEFAULT_SIZE;
        }
        return Math.max(1, Math.min(query.getSize(), PageQuery.MAX_SIZE_ADMIN));
    }
}
