package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.shared.exception.BizException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderConfigQueryServiceTest {
    @Mock ModelProviderMapper providers;
    @Mock ModelMapper models;
    ProviderConfigQueryService service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, ModelProviderDO.class);
        service = new ProviderConfigQueryService(providers, models);
    }

    static ModelProviderDO row(long id) {
        var row = ProviderServiceImplTest.entity();
        row.setId(id);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 31, 12, 0));
        row.setAuthConfig(Map.of("apiKey", Map.of("ciphertext", "not-for-cache")));
        return row;
    }

    @Test
    void filteredListUsesCursorAndLimitPlusOne() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of(row(3), row(2)));
        var query = new ProviderListQuery();
        query.setType("openai");
        query.setEnabled(false);
        query.setSize(1);
        var first = service.list(query);
        assertThat(first.getItems()).hasSize(1);
        assertThat(first.getHasNext()).isTrue();
        assertThat(first.getNextCursor()).isNotBlank();
        query.setCursor(first.getNextCursor());
        service.list(query);
        var wrappers = ArgumentCaptor.forClass(Wrapper.class);
        verify(providers, times(2)).selectList(wrappers.capture());
        String sql = wrappers.getAllValues().get(1).getSqlSegment();
        assertThat(sql).contains("type", "enabled", "created_at", "id", "LIMIT 2")
                .doesNotContain("OFFSET");
        assertThat(sql).contains("created_at, id");
    }

    @Test
    void invalidQueryIsRejectedAndSizeIsBounded() {
        var query = new ProviderListQuery();
        query.setCursor("not-a-cursor");
        assertThatThrownBy(() -> service.list(query)).isInstanceOf(BizException.class);
        query.setCursor(null);
        query.setSize(9999);
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.list(query);
        var wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(providers).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("LIMIT 201");
    }

    @Test
    void missingDetailFailsInsteadOfCachingNull() {
        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(BizException.class);
    }

    @Test
    void detailIncludesModelsButNeverCredentials() {
        var row = row(1);
        row.setAuthConfig(Map.of("apiKey", Map.of("ciphertext", "not-for-cache"),
                "anthropicVersion", "2023-06-01"));
        when(providers.selectOne(any(Wrapper.class))).thenReturn(row);
        var model = new com.hify.ai.internal.entity.ModelDO();
        model.setPublicId("model-public-id");
        model.setModelName("upstream-model");
        model.setEnabled(false);
        model.setCapabilities(Map.of("tool_call", true));
        when(models.selectList(any(Wrapper.class))).thenReturn(List.of(model));
        var response = service.get(row.getPublicId());
        assertThat(response.getModelConfigs()).hasSize(1);
        assertThat(response.getModelConfigs().getFirst().getModelName()).isEqualTo("upstream-model");
        assertThat(response.getModelConfigs().getFirst().getEnabled()).isFalse();
        assertThat(response.getModelConfigs().getFirst().getCapabilities()).containsEntry("tool_call", true);
        assertThat(response.getAuthConfig()).containsEntry("anthropicVersion", "2023-06-01")
                .doesNotContainKey("apiKey");
        assertThat(response.getCredentialsConfigured()).isTrue();
        var wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(models).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment()).contains("provider_id", "sort_order", "id");
    }
}
