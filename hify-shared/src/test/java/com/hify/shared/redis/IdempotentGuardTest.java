package com.hify.shared.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link IdempotentGuard} 的单元测试。 */
@ExtendWith(MockitoExtension.class)
class IdempotentGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotentGuard guard;

    @BeforeEach
    void setUp() {
        // 部分用例不触碰 opsForValue，用 lenient 避免 Mockito 严格模式报无用桩
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        guard = new IdempotentGuard(redisTemplate);
    }

    @Test
    @DisplayName("首次占用成功")
    void 首次占用成功() {
        when(valueOperations.setIfAbsent(eq("order:1"), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(guard.tryAcquire("order:1", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("窗口内重复占用失败")
    void 重复占用失败() {
        when(valueOperations.setIfAbsent(eq("order:1"), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(guard.tryAcquire("order:1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("占用时带上窗口时长")
    void 占用带窗口时长() {
        when(valueOperations.setIfAbsent(eq("order:1"), anyString(), any(Duration.class))).thenReturn(true);

        guard.tryAcquire("order:1", Duration.ofMinutes(5));

        verify(valueOperations).setIfAbsent(eq("order:1"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Redis 返回 null 时视为占用失败")
    void null视为失败() {
        when(valueOperations.setIfAbsent(eq("order:1"), anyString(), any(Duration.class))).thenReturn(null);

        assertThat(guard.tryAcquire("order:1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("释放幂等键")
    void 释放() {
        guard.release("order:1");

        verify(redisTemplate).delete("order:1");
    }

    @Test
    @DisplayName("查询占用状态")
    void 查询占用状态() {
        when(redisTemplate.hasKey("order:1")).thenReturn(true);
        assertThat(guard.isHeld("order:1")).isTrue();

        when(redisTemplate.hasKey("order:1")).thenReturn(false);
        assertThat(guard.isHeld("order:1")).isFalse();
    }
}
