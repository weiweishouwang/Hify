package com.hify.ai.internal.service;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceImplTest {

    @Mock ModelMapper models;
    @Mock ModelProviderMapper providers;
    @Mock ProviderAdapterFactory adapterFactory;
    @Mock ProviderAdapter adapter;

    EmbeddingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingServiceImpl(models, providers, adapterFactory);
        lenient().when(adapterFactory.getAdapter(anyString())).thenReturn(adapter);
    }

    static ModelDO model(String type, boolean enabled) {
        ModelDO model = new ModelDO();
        model.setId(5L);
        model.setPublicId("01MODEL00000000000000000001");
        model.setProviderId(3L);
        model.setModelName("text-embedding-3-small");
        model.setModelType(type);
        model.setEnabled(enabled);
        return model;
    }

    static ModelProviderDO provider(boolean enabled) {
        ModelProviderDO provider = new ModelProviderDO();
        provider.setId(3L);
        provider.setType("openai");
        provider.setEnabled(enabled);
        return provider;
    }

    @Test
    @DisplayName("向量化：按模型找到接入点，交给对应协议适配器")
    void embedsThroughAdapter() {
        when(models.selectOne(any())).thenReturn(model("embedding", true));
        when(providers.selectById(3L)).thenReturn(provider(true));
        when(adapter.embed(any(), anyString(), any())).thenReturn(List.of(new float[] {0.1f, 0.2f}));

        var vectors = service.embed("01MODEL00000000000000000001", List.of("甲"));

        assertThat(vectors).hasSize(1);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f);
        verify(adapter).embed(any(), org.mockito.ArgumentMatchers.eq("text-embedding-3-small"),
                org.mockito.ArgumentMatchers.eq(List.of("甲")));
    }

    @Test
    @DisplayName("模型不是 embedding 类型时拒绝")
    void rejectsNonEmbeddingModel() {
        when(models.selectOne(any())).thenReturn(model("chat", true));

        assertThatThrownBy(() -> service.embed("01MODEL00000000000000000001", List.of("甲")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是 embedding");
    }

    @Test
    @DisplayName("模型或接入点停用时拒绝")
    void rejectsDisabled() {
        when(models.selectOne(any())).thenReturn(model("embedding", false));

        assertThatThrownBy(() -> service.embed("01MODEL00000000000000000001", List.of("甲")))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("模型不存在时报 404")
    void missingModelFails() {
        when(models.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.embed("01MODEL00000000000000000001", List.of("甲")))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("空文本不调用上游")
    void emptyTextsSkipCall() {
        assertThat(service.embed("01MODEL00000000000000000001", List.of())).isEmpty();
    }
}
