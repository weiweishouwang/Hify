package com.hify.knowledge.internal.service;

import com.hify.knowledge.api.DocumentRemovedEvent;
import com.hify.knowledge.api.DocumentUploadedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档事件的监听者。
 *
 * <p><b>一律在事务提交后才动作。</b>索引与清理任务都要立刻回查文档行，
 * 事务没提交时那一行还看不见 —— 任务会扑空然后静默退出，症状是
 * 「文档永远停在 uploaded」或「PG 里的分段删不掉」。
 */
@Component
public class KnowledgeEventListener {

    private final IndexingService indexingService;
    private final DocumentServiceImpl documentService;

    public KnowledgeEventListener(IndexingService indexingService, DocumentServiceImpl documentService) {
        this.indexingService = indexingService;
        this.documentService = documentService;
    }

    /** 文档落库后提交索引任务。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        indexingService.submit(event.documentId());
    }

    /** 文档软删后清理 PG 的分段，再硬删 MySQL 行。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentRemoved(DocumentRemovedEvent event) {
        documentService.scheduleCleanup(event.documentId(), event.kbId());
    }
}
