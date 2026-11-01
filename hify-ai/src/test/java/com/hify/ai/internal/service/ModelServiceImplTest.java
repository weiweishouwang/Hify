package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ProviderAdapter;
import com.hify.ai.internal.spi.ProviderAdapterFactory;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelServiceImplTest {

    @Mock ModelMapper models;
    @Mock ModelProviderMapper providers;
    @Mock ProviderAdapterFactory adapterFactory;
    @Mock ProviderAdapter adapter;

    ModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModelServiceImpl(models, providers, adapterFactory);
        lenient().when(adapterFactory.getAdapter(anyString())).thenReturn(adapter);
    }

    static ModelProviderDO provider() {
        ModelProviderDO provider = new ModelProviderDO();
        provider.setId(3L);
        provider.setPublicId("01PROVIDER00000000000000001");
        provider.setName("OpenAI");
        provider.setType("openai");
        provider.setBaseUrl("https://api.openai.com/v1");
        provider.setEnabled(true);
        return provider;
    }

    static ModelDO model() {
        ModelDO model = new ModelDO();
        model.setId(5L);
        model.setPublicId("01MODEL00000000000000000001");
        model.setProviderId(3L);
        model.setModelName("text-embedding-3-small");
        model.setDisplayName("Embedding 小模型");
        model.setModelType("embedding");
        model.setEnabled(true);
        model.setSortOrder(0);
        model.setConfigVersion(1L);
        model.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        model.setUpdatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return model;
    }

    @Test
    @DisplayName("同步：上游报的新模型落成新行，默认按 chat 认定用途")
    void syncCreatesMissingModels() {
        when(providers.selectOne(any())).thenReturn(provider());
        when(adapter.listModels(any())).thenReturn(List.of("gpt-4o", "text-embedding-3-small"));
        when(models.findIdByProviderAndModel(any(), anyString())).thenReturn(null);
        when(models.listModelNamesByProvider(any())).thenReturn(List.of());

        var result = service.sync("01PROVIDER00000000000000001");

        assertThat(result.getDiscovered()).isEqualTo(2);
        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getDisabled()).isZero();

        var captor = ArgumentCaptor.forClass(ModelDO.class);
        verify(models, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row -> {
            assertThat(row.getProviderId()).isEqualTo(3L);
            assertThat(row.getModelType()).isEqualTo("chat");
            assertThat(row.getEnabled()).isTrue();
            assertThat(row.getConfigVersion()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("同步：上游不再提供的模型被停用，不物理删除")
    void syncDisablesVanishedModels() {
        var vanished = model();
        vanished.setModelName("gpt-3.5-turbo");
        vanished.setEnabled(true);

        when(providers.selectOne(any())).thenReturn(provider());
        when(adapter.listModels(any())).thenReturn(List.of("gpt-4o"));
        when(models.findIdByProviderAndModel(any(), anyString())).thenReturn(null);
        when(models.listModelNamesByProvider(any())).thenReturn(List.of("gpt-4o", "gpt-3.5-turbo"));
        when(models.selectOne(any())).thenReturn(vanished);

        var result = service.sync("01PROVIDER00000000000000001");

        assertThat(result.getDisabled()).isEqualTo(1);
        assertThat(vanished.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("同步：空的上游目录不误伤已有模型")
    void syncWithEmptyCatalogDisablesNothing() {
        when(providers.selectOne(any())).thenReturn(provider());
        when(adapter.listModels(any())).thenReturn(List.of());

        var result = service.sync("01PROVIDER00000000000000001");

        assertThat(result.getDiscovered()).isZero();
        assertThat(result.getDisabled()).isZero();
        verify(models, never()).updateById(any(ModelDO.class));
    }

    @Test
    @DisplayName("更新：把模型标成 embedding 并补上向量维度")
    void updateSetsModelTypeAndDimension() {
        when(models.selectOne(any())).thenReturn(model());
        when(models.update(any(), any())).thenReturn(1);

        var req = new com.hify.ai.api.ModelUpdateReq();
        req.setModelType("embedding");
        req.setEmbeddingDimension(1536);
        req.setConfigVersion(1L);

        var resp = service.update("01MODEL00000000000000000001", req);

        assertThat(resp.getModelType()).isEqualTo("embedding");
        assertThat(resp.getEmbeddingDimension()).isEqualTo(1536);
        assertThat(resp.getConfigVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("更新：配置版本不匹配报冲突")
    void updateRejectsStaleVersion() {
        when(models.selectOne(any())).thenReturn(model());

        var req = new com.hify.ai.api.ModelUpdateReq();
        req.setConfigVersion(99L);

        assertThatThrownBy(() -> service.update("01MODEL00000000000000000001", req))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("列表：多取一条判断还有没有下一页")
    void listUsesCursor() {
        var first = model();
        var second = model();
        second.setId(6L);
        second.setPublicId("01MODEL00000000000000000002");
        second.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(models.selectList(any())).thenReturn(List.of(first, second));
        when(providers.selectBatchIds(any())).thenReturn(List.of(provider()));

        var query = new com.hify.ai.api.ModelListQuery();
        query.setSize(1);
        var resp = service.list(query);

        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getHasNext()).isTrue();
        assertThat(resp.getNextCursor()).isNotBlank();
        assertThat(resp.getItems().get(0).getProviderName()).isEqualTo("OpenAI");
    }
}
