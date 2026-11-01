package com.hify.knowledge.internal.service;

import com.hify.ai.api.ModelService;
import com.hify.ai.api.ModelSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseCreateReq;
import com.hify.knowledge.api.KnowledgeBaseListQuery;
import com.hify.knowledge.api.KnowledgeBaseUpdateReq;
import com.hify.knowledge.internal.converter.KnowledgeConverter;
import com.hify.knowledge.internal.entity.KnowledgeBaseDO;
import com.hify.knowledge.internal.mapper.DocumentMapper;
import com.hify.knowledge.internal.mapper.KnowledgeBaseMapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock KnowledgeBaseMapper knowledgeBases;
    @Mock DocumentMapper documents;
    @Mock ModelService modelService;

    KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseServiceImpl(knowledgeBases, documents, modelService, new KnowledgeConverter());
        lenient().when(knowledgeBases.findIdByName(anyString())).thenReturn(null);
        lenient().when(modelService.get(anyString())).thenReturn(embeddingModel(1536));
    }

    static ModelSummaryResp embeddingModel(int dimension) {
        ModelSummaryResp model = new ModelSummaryResp();
        model.setPublicId("01MODEL0000000000000000001");
        model.setModelName("text-embedding-3-small");
        model.setDisplayName("Embedding 小模型");
        model.setModelType("embedding");
        model.setEnabled(true);
        model.setEmbeddingDimension(dimension);
        return model;
    }

    static KnowledgeBaseCreateReq createRequest() {
        KnowledgeBaseCreateReq req = new KnowledgeBaseCreateReq();
        req.setName("内部手册");
        req.setDescription("团队内部文档");
        req.setEmbeddingModel("01MODEL0000000000000000001");
        req.setChunkSize(500);
        req.setChunkOverlap(50);
        return req;
    }

    static KnowledgeBaseDO entity() {
        KnowledgeBaseDO entity = new KnowledgeBaseDO();
        entity.setId(1L);
        entity.setPublicId("01KB0000000000000000000001");
        entity.setName("内部手册");
        entity.setDescription("团队内部文档");
        entity.setEmbeddingModel("01MODEL0000000000000000001");
        entity.setEmbeddingDim(1536);
        entity.setChunkSize(500);
        entity.setChunkOverlap(50);
        entity.setDocCount(0);
        entity.setStatus("active");
        entity.setConfigVersion(1L);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return entity;
    }

    @Test
    @DisplayName("创建知识库：写进模型标识与维度，分块参数按缺省值归一")
    void createStoresEmbeddingAndChunkParams() {
        var req = createRequest();
        req.setChunkSize(null);
        req.setChunkOverlap(null);

        var resp = service.create(req);

        assertThat(resp.getName()).isEqualTo("内部手册");
        assertThat(resp.getEmbeddingModel()).isEqualTo("01MODEL0000000000000000001");
        assertThat(resp.getEmbeddingDim()).isEqualTo(1536);
        assertThat(resp.getChunkSize()).isEqualTo(500);
        assertThat(resp.getChunkOverlap()).isEqualTo(50);
        assertThat(resp.getConfigVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("创建时名称已被占用（含已删除记录）报冲突")
    void createRejectsDuplicateName() {
        when(knowledgeBases.findIdByName("内部手册")).thenReturn(99L);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("所选模型维度与向量库不一致时拒绝建库")
    void createRejectsDimensionMismatch() {
        when(modelService.get(anyString())).thenReturn(embeddingModel(768));

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("768");
    }

    @Test
    @DisplayName("所选模型还没确认维度时拒绝建库")
    void createRejectsUnknownDimension() {
        var model = embeddingModel(1536);
        model.setEmbeddingDimension(null);
        when(modelService.get(anyString())).thenReturn(model);

        assertThatThrownBy(() -> service.create(createRequest()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("向量维度");
    }

    @Test
    @DisplayName("分块重叠不小于块大小时拒绝")
    void createRejectsInvalidChunkParams() {
        var req = createRequest();
        req.setChunkSize(200);
        req.setChunkOverlap(200);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重叠");
    }

    @Test
    @DisplayName("更新时配置版本不匹配报冲突")
    void updateRejectsStaleVersion() {
        when(knowledgeBases.selectOne(any())).thenReturn(entity());

        var req = new KnowledgeBaseUpdateReq();
        req.setName("内部手册");
        req.setConfigVersion(99L);

        assertThatThrownBy(() -> service.update("01KB0000000000000000000001", req))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("更新时名称改成别人的名字报冲突")
    void updateRejectsDuplicateName() {
        when(knowledgeBases.selectOne(any())).thenReturn(entity());
        when(knowledgeBases.findIdByName("别人的名字")).thenReturn(99L);

        var req = new KnowledgeBaseUpdateReq();
        req.setName("别人的名字");
        req.setConfigVersion(1L);

        assertThatThrownBy(() -> service.update("01KB0000000000000000000001", req))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("库里有文档时不允许删除")
    void deleteRefusesWhenDocumentsExist() {
        when(knowledgeBases.selectOne(any())).thenReturn(entity());
        when(documents.countByKbId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete("01KB0000000000000000000001"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("列表用游标分页，多取一条判断还有没有下一页")
    void listUsesCursor() {
        var first = entity();
        var second = entity();
        second.setId(2L);
        second.setPublicId("01KB0000000000000000000002");
        second.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(knowledgeBases.selectList(any())).thenReturn(List.of(first, second));

        var query = new KnowledgeBaseListQuery();
        query.setSize(1);
        var resp = service.list(query);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isTrue();
        assertThat(resp.getNextCursor()).isNotBlank();
    }

    @Test
    @DisplayName("可选的 embedding 模型列表只给启用且类型正确的")
    void listsEmbeddingModelOptions() {
        var model = embeddingModel(1536);
        var page = new com.hify.ai.api.ModelListResp();
        page.setItems(List.of(model));
        page.setHasNext(false);
        when(modelService.list(any())).thenReturn(page);

        var options = service.listEmbeddingModels();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getPublicId()).isEqualTo("01MODEL0000000000000000001");
        var captor = ArgumentCaptor.forClass(com.hify.ai.api.ModelListQuery.class);
        verify(modelService).list(captor.capture());
        assertThat(captor.getValue().getModelType()).isEqualTo("embedding");
        assertThat(captor.getValue().getEnabled()).isTrue();
    }
}
