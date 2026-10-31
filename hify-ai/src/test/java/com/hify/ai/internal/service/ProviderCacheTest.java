package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderListQuery;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.mapper.ProviderHealthMapper;
import com.hify.ai.internal.security.ProviderCredentialCipher;
import com.hify.shared.config.CacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 缓存/事务代理；仅测试替身缓存和事务资源，不连接 Redis 或数据库。 */
class ProviderCacheTest {
    AnnotationConfigApplicationContext context;
    ProviderServiceImpl service;
    ProviderConfigQueryService queries;
    ModelProviderMapper providers;
    ProviderHealthMapper health;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        service = context.getBean(ProviderServiceImpl.class);
        queries = context.getBean(ProviderConfigQueryService.class);
        providers = context.getBean(ModelProviderMapper.class);
        when(providers.findIdByName(anyString())).thenReturn(null);
        health = context.getBean(ProviderHealthMapper.class);
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void listKeysIncludeAllFiltersAndCacheHitsSkipDatabase() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        var query = new ProviderListQuery();
        service.list(query);
        service.list(query);
        query.setEnabled(false);
        service.list(query);
        query.setType("ollama");
        service.list(query);
        query.setSize(1);
        service.list(query);
        verify(providers, times(4)).selectList(any(Wrapper.class));
    }

    @Test
    void detailConfigurationIsCachedButHealthIsFresh() {
        var provider = ProviderServiceImplTest.entity();
        when(providers.selectOne(any(Wrapper.class))).thenReturn(provider);
        when(context.getBean(ModelMapper.class).selectList(any(Wrapper.class))).thenReturn(List.of());
        var first = new ProviderHealthDO();
        first.setStatus("UP");
        first.setProviderConfigVersion(1L);
        var second = new ProviderHealthDO();
        second.setStatus("DOWN");
        second.setProviderConfigVersion(1L);
        when(health.selectById(1L)).thenReturn(first, second);
        assertThat(service.get(provider.getPublicId()).getProviderHealth().getStatus()).isEqualTo("UP");
        assertThat(service.get(provider.getPublicId()).getProviderHealth().getStatus()).isEqualTo("DOWN");
        verify(providers).selectOne(any(Wrapper.class));
        verify(health, times(2)).selectById(1L);
    }

    @Test
    void createEvictsCachedListsAfterCommit() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.list(new ProviderListQuery());
        when(providers.insert(any(ModelProviderDO.class))).thenAnswer(call -> {
            call.<ModelProviderDO>getArgument(0).setId(2L);
            return 1;
        });
        service.create(ollamaRequest());
        service.list(new ProviderListQuery());
        verify(providers, times(2)).selectList(any(Wrapper.class));
    }

    @Test
    void updateAndDeleteEvictListAndDetailEntries() {
        var provider = ProviderServiceImplTest.entity();
        when(providers.selectOne(any(Wrapper.class))).thenReturn(provider);
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(context.getBean(ModelMapper.class).selectList(any(Wrapper.class))).thenReturn(List.of());
        service.list(new ProviderListQuery());
        queries.get(provider.getPublicId());
        when(providers.update(any(ModelProviderDO.class), any(Wrapper.class))).thenReturn(1);
        service.update(provider.getPublicId(), ProviderServiceImplTest.updateRequest());
        assertThat(context.getBean(CacheManager.class).getCache(CacheConfig.PROVIDER_CACHE)
                .get("detail:" + provider.getPublicId())).isNull();
        service.list(new ProviderListQuery());
        when(providers.deleteById(1L)).thenReturn(1);
        service.delete(provider.getPublicId());
        service.list(new ProviderListQuery());
        verify(providers, times(3)).selectList(any(Wrapper.class));
    }

    @Test
    void failedTransactionDoesNotEvictExistingCache() {
        when(providers.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.list(new ProviderListQuery());
        when(providers.insert(any(ModelProviderDO.class))).thenAnswer(call -> {
            call.<ModelProviderDO>getArgument(0).setId(2L);
            return 1;
        });
        context.getBean(TestTransactionManager.class).failCommit = true;
        assertThatThrownBy(() -> service.create(ollamaRequest())).isInstanceOf(RuntimeException.class);
        service.list(new ProviderListQuery());
        verify(providers).selectList(any(Wrapper.class));
    }

    private ProviderCreateReq ollamaRequest() {
        var request = new ProviderCreateReq();
        request.setName("Local");
        request.setType("ollama");
        request.setBaseUrl("http://ollama:11434");
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean ModelProviderMapper providers() { return mock(ModelProviderMapper.class); }
        @Bean ModelMapper models() { return mock(ModelMapper.class); }
        @Bean ProviderHealthMapper health() { return mock(ProviderHealthMapper.class); }
        @Bean ProviderCredentialCipher cipher() { return new ProviderCredentialCipher(""); }
        @Bean ProviderConnectivityService connectivity() { return mock(ProviderConnectivityService.class); }
        @Bean CacheManager cacheManager() {
            return new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager(CacheConfig.PROVIDER_CACHE));
        }
        @Bean TestTransactionManager transactionManager() { return new TestTransactionManager(); }
        @Bean ProviderConfigQueryService queries(ModelProviderMapper providers, ModelMapper models) {
            return new ProviderConfigQueryService(providers, models);
        }
        @Bean ProviderServiceImpl service(ModelProviderMapper providers, ModelMapper models,
                                          ProviderHealthMapper health, ProviderConfigQueryService queries,
                                          ProviderCredentialCipher cipher, ProviderConnectivityService connectivity) {
            return new ProviderServiceImpl(providers, models, health, queries, cipher, connectivity);
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        boolean failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) { throw new IllegalStateException("模拟提交失败"); }
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
