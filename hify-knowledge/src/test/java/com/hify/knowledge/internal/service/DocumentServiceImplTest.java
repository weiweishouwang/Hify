package com.hify.knowledge.internal.service;

import com.hify.knowledge.api.DocumentUpload;
import com.hify.knowledge.api.DocumentRemovedEvent;
import com.hify.knowledge.api.DocumentUploadedEvent;
import com.hify.knowledge.internal.converter.KnowledgeConverter;
import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.internal.repository.VecChunkRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock DocumentMapper documents;
    @Mock KnowledgeBaseMapper knowledgeBases;
    @Mock VecChunkRepository chunks;
    @Mock ApplicationEventPublisher events;

    DocumentServiceImpl service;

    /** 同步执行器：测试里让异步清理立即跑完。 */
    static final Executor DIRECT = Runnable::run;

    @BeforeEach
    void setUp() {
        service = new DocumentServiceImpl(documents, knowledgeBases, chunks,
                new KnowledgeConverter(), events, DIRECT);
    }

    static KnowledgeBaseDO knowledgeBase() {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setId(7L);
        kb.setPublicId("01KB0000000000000000000001");
        kb.setName("内部手册");
        kb.setEmbeddingModel("01MODEL0000000000000000001");
        kb.setEmbeddingDim(1536);
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setStatus("active");
        kb.setConfigVersion(1L);
        return kb;
    }

    static DocumentDO document() {
        DocumentDO doc = new DocumentDO();
        doc.setId(9L);
        doc.setPublicId("01DOC0000000000000000000001");
        doc.setKbId(7L);
        doc.setName("手册.txt");
        doc.setFileType("txt");
        doc.setFileSize(11L);
        doc.setContent("Hify 内部手册");
        doc.setStatus("ready");
        doc.setChunkCount(2);
        doc.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        doc.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return doc;
    }

    static DocumentUpload upload(String name, String content) {
        return new DocumentUpload(name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("上传 TXT：原文落库，状态 uploaded，并提交异步索引")
    void uploadStoresContentAndSubmitsIndex() {
        when(knowledgeBases.selectOne(any())).thenReturn(knowledgeBase());

        var resp = service.upload("01KB0000000000000000000001", upload("手册.txt", "Hify 内部手册"));

        assertThat(resp.getName()).isEqualTo("手册.txt");
        assertThat(resp.getStatus()).isEqualTo(KnowledgeSupport.DOC_UPLOADED);
        assertThat(resp.getFileType()).isEqualTo("txt");
        assertThat(resp.getContentHash()).hasSize(64);

        var captor = ArgumentCaptor.forClass(DocumentDO.class);
        verify(documents).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Hify 内部手册");
        verify(events).publishEvent(any(DocumentUploadedEvent.class));
    }

    @Test
    @DisplayName("非 TXT 文件被拒绝")
    void uploadRejectsNonTxt() {
        when(knowledgeBases.selectOne(any())).thenReturn(knowledgeBase());

        assertThatThrownBy(() -> service.upload("01KB0000000000000000000001",
                upload("手册.pdf", "x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("TXT");
    }

    @Test
    @DisplayName("空文件被拒绝")
    void uploadRejectsEmptyContent() {
        when(knowledgeBases.selectOne(any())).thenReturn(knowledgeBase());

        assertThatThrownBy(() -> service.upload("01KB0000000000000000000001", upload("空.txt", "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内容为空");
    }

    @Test
    @DisplayName("超过 10 MB 的文件被拒绝")
    void uploadRejectsOversizedFile() {
        when(knowledgeBases.selectOne(any())).thenReturn(knowledgeBase());
        byte[] big = new byte[(int) KnowledgeSupport.MAX_UPLOAD_BYTES + 1];

        assertThatThrownBy(() -> service.upload("01KB0000000000000000000001",
                new DocumentUpload("大.txt", "text/plain", big)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    @DisplayName("删除：先软删并置 deleting，清理 PG 的动作交给提交后的事件")
    void deleteSoftDeletesThenPublishesCleanupEvent() {
        when(documents.selectOne(any())).thenReturn(document());
        when(documents.deleteById(any(java.io.Serializable.class))).thenReturn(1);

        service.delete("01DOC0000000000000000000001");

        var update = ArgumentCaptor.forClass(DocumentDO.class);
        verify(documents).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isEqualTo(KnowledgeSupport.DOC_DELETING);
        verify(documents).deleteById((java.io.Serializable) Long.valueOf(9L));
        verify(events).publishEvent(any(DocumentRemovedEvent.class));
        verify(chunks, never()).deleteByDocId(any());
    }

    @Test
    @DisplayName("重新索引：状态回到 uploaded 并重新提交任务")
    void reindexResetsStatus() {
        when(documents.selectOne(any())).thenReturn(document());

        var resp = service.reindex("01DOC0000000000000000000001");

        assertThat(resp.getStatus()).isEqualTo(KnowledgeSupport.DOC_UPLOADED);
        verify(events).publishEvent(any(DocumentUploadedEvent.class));
    }

    @Test
    @DisplayName("文档不存在时报 404")
    void getMissingDocumentFails() {
        when(documents.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.get("01DOC0000000000000000000001"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("文档列表按知识库过滤并用游标分页")
    void listUsesCursor() {
        when(knowledgeBases.selectOne(any())).thenReturn(knowledgeBase());
        when(documents.selectList(any())).thenReturn(List.of(document()));

        var resp = service.list("01KB0000000000000000000001", null);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isFalse();
        assertThat(resp.getItems().get(0).getKbPublicId()).isEqualTo("01KB0000000000000000000001");
    }
}
