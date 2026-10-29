package com.hify.shared.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link RateLimiter} 的单元测试。 */
@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // 部分用例不触碰 opsForValue，用 lenient 避免 Mockito 严格模式报无用桩
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RateLimiter(redisTemplate);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("计数未超限时有配额")
    void 未超限() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(2L);

        assertThat(rateLimiter.tryAcquire("user:1:openai", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("计数超限时无配额")
    void 超限() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(4L);

        assertThat(rateLimiter.tryAcquire("user:1:openai", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("计数恰好等于上限时仍有配额")
    void 恰好等于上限() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(3L);

        assertThat(rateLimiter.tryAcquire("user:1:openai", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("Redis 返回 null 时视为无配额")
    void null视为无配额() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertThat(rateLimiter.tryAcquire("user:1:openai", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("把窗口毫秒数传给脚本")
    void 传窗口毫秒() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        rateLimiter.tryAcquire("user:1:openai", 3, Duration.ofSeconds(45));

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("45000"));
    }

    @Test
    @DisplayName("清空计数")
    void 清空计数() {
        rateLimiter.reset("user:1:openai");

        verify(redisTemplate).delete("user:1:openai");
    }

    @Test
    @DisplayName("读取当前计数")
    void 读取当前计数() {
        when(valueOperations.get("user:1:openai")).thenReturn("7");
        assertThat(rateLimiter.currentCount("user:1:openai")).isEqualTo(7L);
    }

    @Test
    @DisplayName("键不存在时计数为 0")
    void 键不存在计数为零() {
        when(valueOperations.get("user:1:openai")).thenReturn(null);

        assertThat(rateLimiter.currentCount("user:1:openai")).isZero();
    }

    @Test
    @DisplayName("计数内容非数字时按 0 处理")
    void 非数字按零处理() {
        when(valueOperations.get("user:1:openai")).thenReturn("abc");

        assertThat(rateLimiter.currentCount("user:1:openai")).isZero();
    }
}
