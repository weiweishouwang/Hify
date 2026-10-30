package com.hify.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link CacheConfig} 的单元测试。 */
class CacheConfigTest {

    private static RedisCacheManager buildManager() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection()).thenReturn(mock(RedisConnection.class));
        RedisCacheManager manager =
                (RedisCacheManager) new CacheConfig().cacheManager(connectionFactory);
        // 关键：Spring 初始化这个 bean 时会调 afterPropertiesSet()，
        // 里面 loadCaches() 会按 initialCacheConfiguration 把各个缓存**预先建出来**。
        // 不调这一步，getCache(name) 会找不到初始配置、退回默认 TTL——
        // 这正是 withInitialCacheConfigurations 最容易踩的坑。
        manager.afterPropertiesSet();
        return manager;
    }

    /**
     * 取某个缓存实际生效的配置。
     *
     * <p>注意 {@code getCacheConfigurations()} 在缓存被创建之前是空的——
     * 初始配置要等 {@code getCache(name)} 触发懒创建之后才会登记进去。
     */
    private static RedisCacheConfiguration configOf(RedisCacheManager manager, String name) {
        assertThat(manager.getCache(name)).isNotNull();
        return manager.getCacheConfigurations().get(name);
    }

    @Test
    @DisplayName("provider-cache 与 agent-cache 都是 30 分钟")
    void providerAndAgentCacheAreThirtyMinutes() {
        RedisCacheManager manager = buildManager();
        assertThat(configOf(manager, CacheConfig.PROVIDER_CACHE).getTtl())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(configOf(manager, CacheConfig.AGENT_CACHE).getTtl())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("session-cache 是 2 小时")
    void sessionCacheIsTwoHours() {
        RedisCacheManager manager = buildManager();
        assertThat(configOf(manager, CacheConfig.SESSION_CACHE).getTtl())
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    @DisplayName("没单独配的缓存走默认 TTL 30 分钟")
    void unknownCacheUsesDefaultTtl() {
        RedisCacheManager manager = buildManager();
        assertThat(configOf(manager, "whatever-cache").getTtl())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("缓存 key 带 hify: 前缀")
    void cacheKeysArePrefixed() {
        RedisCacheManager manager = buildManager();
        assertThat(configOf(manager, CacheConfig.PROVIDER_CACHE).getKeyPrefix().compute("x"))
                .startsWith("hify:");
    }

    @Test
    @DisplayName("缓存名保留原样，前缀只加在 key 上")
    void cacheNameKeepsItsOwnName() {
        RedisCacheManager manager = buildManager();
        RedisCache cache = (RedisCache) manager.getCache(CacheConfig.PROVIDER_CACHE);
        assertThat(cache.getName()).isEqualTo(CacheConfig.PROVIDER_CACHE);
    }
}
