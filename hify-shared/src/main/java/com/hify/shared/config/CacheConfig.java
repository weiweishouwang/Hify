package com.hify.shared.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.hify.shared.json.TimeSerializers;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 配置。
 *
 * <p>缓存统一落在 Redis：**不用本地 Caffeine**——多副本部署时本地缓存各写各的，
 * 一致性问题排查起来很痛（CLAUDE.md §1.4）。代价是每次读缓存多一次网络往返，
 * 相对于一次数据库查询或一次 LLM 调用，这点开销可以忽略。
 *
 * <p>TTL 统一在这里定死，不散落到各个 {@code @Cacheable} 上：默认 30 分钟，
 * 少数缓存按名字单独配。
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    /** 模型接入点缓存。 */
    public static final String PROVIDER_CACHE = "provider-cache";

    /** Agent 配置缓存。 */
    public static final String AGENT_CACHE = "agent-cache";

    /** 会话缓存。 */
    public static final String SESSION_CACHE = "session-cache";

    /** 默认 TTL：30 分钟。 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /** 会话缓存 TTL：2 小时——会话比配置活得久。 */
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    /** 缓存 key 前缀，避免与同一个 Redis 实例上的其他应用撞键。 */
    private static final String KEY_PREFIX = "hify:";

    /**
     * Redis 缓存管理器。
     *
     * <p>值的序列化用 JSON 而不是 JDK 序列化：JDK 序列化要求 DTO 实现
     * {@link java.io.Serializable}，而 DTO 通常不会实现——第一次缓存就会抛
     * 「Cannot serialize」。这里用独立的 ObjectMapper（**不是容器里那个**，
     * 否则会给 API 响应加上类型信息），时间格式与 API 层保持一致。
     *
     * <p>保留空值缓存：查不到的结果也缓存住，避免不存在的 key 反复打到库上
     * （缓存穿透）。
     *
     * @param connectionFactory Redis 连接
     * @return 缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .prefixCacheNameWith(KEY_PREFIX)
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper())));

        Map<String, RedisCacheConfiguration> byName = new HashMap<>();
        byName.put(PROVIDER_CACHE, defaultConfig.entryTtl(DEFAULT_TTL)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(providerCacheObjectMapper()))));
        byName.put(AGENT_CACHE, defaultConfig.entryTtl(DEFAULT_TTL));
        byName.put(SESSION_CACHE, defaultConfig.entryTtl(SESSION_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .transactionAware()
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(byName)
                .build();
    }

    /**
     * 接入点缓存需要恢复不同的 DTO 类型。只给此缓存启用受限类型信息，
     * 不改变其他缓存的既有格式，也不修改 API 使用的 ObjectMapper。
     */
    private static ObjectMapper providerCacheObjectMapper() {
        ObjectMapper mapper = cacheObjectMapper();
        var validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.hify.ai.api.")
                .allowIfSubType(java.util.ArrayList.class)
                .allowIfSubType(java.util.LinkedHashMap.class)
                .allowIfSubType(java.math.BigDecimal.class)
                .allowIfSubType(java.math.BigInteger.class)
                .allowIfSubType(org.springframework.cache.support.NullValue.class)
                .build();
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        return mapper;
    }

    /**
     * 缓存专用的 ObjectMapper。
     *
     * <p>刻意不复用容器里那个，避免缓存使用的类型信息进入 API 响应。
     * 传入自定义 mapper 时，GenericJackson2JsonRedisSerializer 不会替我们开启
     * 默认类型信息；provider-cache 单独通过受限白名单配置类型恢复。
     * 其他缓存维持原有格式。
     *
     * @return 缓存专用的 ObjectMapper
     */
    private static ObjectMapper cacheObjectMapper() {
        return JsonMapper.builder()
                .addModule(TimeSerializers.timeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
