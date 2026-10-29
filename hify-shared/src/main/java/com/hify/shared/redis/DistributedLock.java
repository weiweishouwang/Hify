package com.hify.shared.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 分布式锁。
 *
 * <p>用于多副本部署下保证"同一资源同一时刻只有一个实例在处理"——例如重复提交、
 * 同一份文档的并发索引。
 *
 * <p>实现要点：
 * <ul>
 *   <li>加锁用 {@code SET key token NX PX ttl}，原子且自带过期时间，持有者崩溃不会死锁</li>
 *   <li>token 是每次加锁随机生成的，**解锁必须比对 token**，否则会误删别人持有的锁</li>
 *   <li>解锁用 Lua 脚本「比对并删除」，避免 GET 与 DEL 之间被别的线程插入</li>
 * </ul>
 *
 * <p>**锁不是幂等性保证**——重复提交这类场景请用 {@link IdempotentGuard}。
 */
@Component
public class DistributedLock {

    /** 比对 token 后再删除，避免删掉别人持有的锁。 */
    static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            else
              return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public DistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试加锁。
     *
     * @param key 锁的键
     * @param ttl 锁的持有时间，到期自动释放
     * @return 持有令牌；未拿到锁时返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /**
     * 解锁。只有当前持有者（token 匹配）才能真正释放。
     *
     * @param key   锁的键
     * @param token {@link #tryLock} 返回的令牌
     * @return 是否由本次调用释放
     */
    public boolean unlock(String key, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        return deleted != null && deleted > 0;
    }

    /**
     * 当前是否被锁住。
     */
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
