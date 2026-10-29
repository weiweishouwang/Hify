package com.hify.shared.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 幂等守卫：保证同一把"业务键"在时间窗内只被处理一次。
 *
 * <p>典型用途是**防止重复提交导致 LLM 被调用两次**——一次重复请求就多烧一份 token，
 * 而 LLM 调用本身有副作用（扣费、发消息、触发工具），不幂等。
 *
 * <p>实现是 {@code SET key value NX PX ttl}，原子且自带过期时间，不存在"检查后再设置"
 * 的竞态；进程崩溃后键会自然过期，不会永久卡住。
 */
@Component
public class IdempotentGuard {

    private final StringRedisTemplate redisTemplate;

    public IdempotentGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试占用幂等键。
     *
     * @param key 业务键
     * @param ttl  幂等窗口时长
     * @return true 表示首次占用（应当继续处理）；false 表示窗口内已占用过（应当跳过）
     */
    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, UUID.randomUUID().toString(), ttl);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放幂等键。
     *
     * <p>仅在处理失败、需要允许重试时调用；处理成功应当让它自然过期，
     * 这样窗口内的重复请求仍然会被挡住。
     */
    public void release(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 幂等键当前是否仍被占用。
     */
    public boolean isHeld(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
