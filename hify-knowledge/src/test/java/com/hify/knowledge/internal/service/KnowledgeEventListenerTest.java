package com.hify.knowledge.internal.service;

import com.hify.knowledge.api.DocumentRemovedEvent;
import com.hify.knowledge.api.DocumentUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/** 事件监听者：事务提交后才动作。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeEventListenerTest {

    @Mock IndexingService indexingService;
    @Mock DocumentServiceImpl documentService;

    KnowledgeEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new KnowledgeEventListener(indexingService, documentService);
    }

    @Test
    @DisplayName("文档落库后提交索引任务")
    void uploadsTriggerIndexing() {
        listener.onDocumentUploaded(new DocumentUploadedEvent(9L));
        verify(indexingService).submit(9L);
    }

    @Test
    @DisplayName("文档软删后清理 PG 分段并硬删 MySQL 行")
    void removalsTriggerCleanup() {
        listener.onDocumentRemoved(new DocumentRemovedEvent(9L, 7L));
        verify(documentService).scheduleCleanup(9L, 7L);
    }
}
