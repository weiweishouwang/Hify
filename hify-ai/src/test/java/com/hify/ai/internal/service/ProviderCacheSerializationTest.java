package com.hify.ai.internal.service;

import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderListResp;
import com.hify.ai.internal.converter.ProviderConverter;
import com.hify.shared.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderCacheSerializationTest {
    @Test
    void configuredRedisSerializerRestoresTypedProviderValuesAndDates() {
        var manager = (RedisCacheManager) new CacheConfig().cacheManager(mock(RedisConnectionFactory.class));
        manager.afterPropertiesSet();
        assertThat(manager.isTransactionAware()).isTrue();
        var cache = (org.springframework.cache.transaction.TransactionAwareCacheDecorator)
                manager.getCache(CacheConfig.PROVIDER_CACHE);
        var redis = (org.springframework.data.redis.cache.RedisCache) cache.getTargetCache();
        var pair = redis.getCacheConfiguration().getValueSerializationPair();
        var provider = ProviderConfigQueryServiceTest.row(1);
        provider.setExtraParams(java.util.Map.of("options", java.util.Map.of("values", List.of("a", "b")),
                "temperature", new java.math.BigDecimal("0.7")));
        var model = new com.hify.ai.internal.entity.ModelDO();
        model.setPublicId("model-one");
        model.setCapabilities(java.util.Map.of("tool_call", true));
        var detail = ProviderConverter.toConfig(provider, List.of(model));
        Object restored = roundTrip(pair, detail);
        assertThat(restored).isInstanceOf(ProviderConfigResp.class);
        var response = (ProviderConfigResp) restored;
        assertThat(response.getCreatedAt()).isEqualTo(detail.getCreatedAt());
        assertThat(response.getAuthConfig()).doesNotContainKey("apiKey");
        assertThat(response.getModelConfigs()).hasSize(1);
        assertThat(response.getModelConfigs().getFirst().getCapabilities()).containsEntry("tool_call", true);
        assertThat(response.getExtraParams()).isEqualTo(detail.getExtraParams());
        var forbidden = java.nio.ByteBuffer.wrap("{\"@class\":\"java.lang.Runtime\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> pair.read(forbidden))
                .isInstanceOf(org.springframework.data.redis.serializer.SerializationException.class);
        var page = new ProviderListResp();
        page.setItems(new ArrayList<>(List.of(ProviderConverter.toSummary(ProviderConfigQueryServiceTest.row(1)))));
        page.setHasNext(false);
        assertThat(roundTrip(pair, page)).isInstanceOfSatisfying(ProviderListResp.class,
                value -> assertThat(value.getItems().getFirst().getPublicId()).isEqualTo(detail.getPublicId()));
    }

    private Object roundTrip(RedisSerializationContext.SerializationPair<?> pair, Object value) {
        @SuppressWarnings("unchecked")
        var serializer = (RedisSerializationContext.SerializationPair<Object>) pair;
        return serializer.read(serializer.write(value));
    }
}
