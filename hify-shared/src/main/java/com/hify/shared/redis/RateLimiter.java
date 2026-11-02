package com.hify.shared.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 限流器。
 *
 * <p>两种用法，**别混着**：
 *
 * <ul>
 *   <li><b>固定窗口计数</b>（{@link #tryAcquire}）—— 限制「一段时间内最多发生多少次」，
 *       用于按分钟计数的场景。用完不释放。</li>
 *   <li><b>并发在途计数</b>（{@link #tryAcquireInFlight} + {@link #releaseInFlight}）——
 *       限制「同一时刻最多有多少个还在跑」。用于「每用户每 provider 在途上限」
 *       （CLAUDE.md §3.5）：一个人开多个标签页，不能让他把整个连接池吃光。</li>
 * </ul>
 *
 * <p>用 Lua 脚本把「计数 + 首次设置过期时间」做成原子操作：
 * 如果分开写 {@code INCR} 与 {@code EXPIRE}，进程在两者之间崩溃会让计数键
 * 永不过期，用户被永久限流。
 */
@Component
public class RateLimiter {

    /** 计数并在首次计数时设置过期时间。 */
    static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    /** 在途计数：超限就回滚（否则多出来的那个永远占着名额）。 */
    static final RedisScript<Long> ACQUIRE_INFLIGHT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
              redis.call('DECR', KEYS[1])
              return 0
            end
            return current
            """, Long.class);

    /** 释放一个在途名额；减到 0 就把键删掉，别留一个 0 值等过期。 */
    static final RedisScript<Long> RELEASE_INFLIGHT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('DECR', KEYS[1])
            if current <= 0 then
              redis.call('DEL', KEYS[1])
              return 0
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取一次配额。
     *
     * @param key    计数键
     * @param limit  窗口内允许的最大次数
     * @param window 计数窗口
     * @return true 表示还有配额；false 表示已超限
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Long count = redisTemplate.execute(ACQUIRE_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        return count != null && count <= limit;
    }

    /**
     * 占一个「在途」名额。
     *
     * <p>与固定窗口计数的区别：<b>用完要释放</b>。调用方在 finally 里调
     * {@link #releaseInFlight(String)}。
     *
     * <p>超限时脚本会把刚加的那个减回去 —— 否则多出来的那一个会一直占着名额，
     * 直到 TTL 到期，用户再也发不出请求。
     *
     * @param key   计数键
     * @param limit 同时在途的上限
     * @param ttl   兜底过期时间：进程崩了没释放时，键到期自动消失
     * @return true 表示拿到名额；false 表示已达上限
     */
    public boolean tryAcquireInFlight(String key, int limit, Duration ttl) {
        Long count = redisTemplate.execute(ACQUIRE_INFLIGHT_SCRIPT, List.of(key),
                String.valueOf(limit), String.valueOf(ttl.toMillis()));
        return count != null && count > 0;
    }

    /**
     * 释放一个「在途」名额。
     *
     * @param key 计数键
     */
    public void releaseInFlight(String key) {
        redisTemplate.execute(RELEASE_INFLIGHT_SCRIPT, List.of(key));
    }

    /**
     * 清空计数。
     */
    public void reset(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 当前窗口内已使用的次数；键不存在时返回 0。
     */
    public long currentCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
