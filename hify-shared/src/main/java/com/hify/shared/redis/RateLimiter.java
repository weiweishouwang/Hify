package com.hify.shared.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 限流器（固定窗口计数）。
 *
 * <p>典型用途是**限制单个用户对单个 provider 的并发在途数**——防止一个人开多个
 * 标签页把整个 LLM 连接池吃光（见 CLAUDE.md §3.5）。
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
