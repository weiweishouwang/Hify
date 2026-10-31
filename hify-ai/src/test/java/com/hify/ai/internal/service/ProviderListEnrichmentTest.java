package com.hify.ai.internal.service;

import com.hify.ai.api.ProviderListItemResp;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.api.ProviderSummaryResp;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.entity.ProviderModelCount;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.ai.internal.spi.ProviderAdapterFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderListEnrichmentTest {
    @Test
    void cachedConfigurationIsNotMutatedAndHealthAndCountsAreFresh() {
        var queries = mock(ProviderConfigQueryService.class);
        var models = mock(ModelMapper.class);
        var health = mock(ProviderHealthMapper.class);
        var service = new ProviderServiceImpl(mock(ModelProviderMapper.class), models, health, queries,
                mock(ProviderCredentialCipher.class), mock(ProviderAdapterFactory.class));
        var summary = new ProviderSummaryResp();
        summary.setId(1L);
        summary.setPublicId("provider-one");
        summary.setConfigVersion(2L);
        var cached = new ProviderListResp();
        cached.setItems(new ArrayList<>(List.of(summary)));
        cached.setHasNext(true);
        cached.setNextCursor("next");
        var query = new ProviderListQuery();
        when(queries.list(query)).thenReturn(cached);
        var first = new ProviderHealthDO();
        first.setProviderId(1L);
        first.setStatus("UP");
        first.setLatencyMs(0L);
        first.setProviderConfigVersion(2L);
        var second = new ProviderHealthDO();
        second.setProviderId(1L);
        second.setStatus("DOWN");
        second.setProviderConfigVersion(2L);
        when(health.selectByIds(List.of(1L))).thenReturn(List.of(first), List.of(second));
        when(models.countEnabledByProviderIds(List.of(1L))).thenReturn(List.of(new ProviderModelCount(1L, 3L)));
        var result = service.list(query);
        var row = (ProviderListItemResp) result.getItems().getFirst();
        assertThat(row.getProviderHealth().getStatus()).isEqualTo("UP");
        assertThat(row.getProviderHealth().getLatencyMs()).isZero();
        assertThat(row.getEnabledModelCount()).isEqualTo(3L);
        assertThat(result.getNextCursor()).isEqualTo("next");
        assertThat(result.getHasNext()).isTrue();
        assertThat(((ProviderListItemResp) service.list(query).getItems().getFirst())
                .getProviderHealth().getStatus()).isEqualTo("DOWN");
        assertThat(cached.getItems().getFirst()).isSameAs(summary).isNotInstanceOf(ProviderListItemResp.class);
        verify(health, times(2)).selectByIds(List.of(1L));
    }

    @Test
    void emptyPageSkipsAllEnrichmentQueries() {
        var queries = mock(ProviderConfigQueryService.class);
        var models = mock(ModelMapper.class);
        var health = mock(ProviderHealthMapper.class);
        var cached = new ProviderListResp();
        cached.setItems(List.of());
        cached.setHasNext(false);
        when(queries.list(any())).thenReturn(cached);
        var service = new ProviderServiceImpl(mock(ModelProviderMapper.class), models, health, queries,
                mock(ProviderCredentialCipher.class), mock(ProviderAdapterFactory.class));
        assertThat(service.list(new ProviderListQuery()).getItems()).isEmpty();
        verifyNoInteractions(models, health);
    }

    @Test
    void missingCountsAndOldHealthVersionFallBackToUnknownAndZero() {
        var queries = mock(ProviderConfigQueryService.class);
        var models = mock(ModelMapper.class);
        var health = mock(ProviderHealthMapper.class);
        var summary = new ProviderSummaryResp();
        summary.setId(1L);
        summary.setConfigVersion(2L);
        var cached = new ProviderListResp();
        cached.setItems(List.of(summary));
        when(queries.list(any())).thenReturn(cached);
        var oldHealth = new ProviderHealthDO();
        oldHealth.setProviderId(1L);
        oldHealth.setProviderConfigVersion(1L);
        oldHealth.setStatus("UP");
        when(health.selectByIds(List.of(1L))).thenReturn(List.of(oldHealth));
        var service = new ProviderServiceImpl(mock(ModelProviderMapper.class), models, health, queries,
                mock(ProviderCredentialCipher.class), mock(ProviderAdapterFactory.class));
        var row = (ProviderListItemResp) service.list(new ProviderListQuery()).getItems().getFirst();
        assertThat(row.getProviderHealth().getStatus()).isEqualTo("UNKNOWN");
        assertThat(row.getEnabledModelCount()).isZero();
    }
}
