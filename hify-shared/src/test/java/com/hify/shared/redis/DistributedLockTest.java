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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link DistributedLock} 的单元测试。 */
@ExtendWith(MockitoExtension.class)
class DistributedLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLock lock;

    @BeforeEach
    void setUp() {
        // 部分用例不触碰 opsForValue，用 lenient 避免 Mockito 严格模式报无用桩
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lock = new DistributedLock(redisTemplate);
    }

    @Test
    @DisplayName("加锁成功返回持有令牌")
    void 加锁成功() {
        when(valueOperations.setIfAbsent(eq("k"), anyString(), any(Duration.class))).thenReturn(true);

        String token = lock.tryLock("k", Duration.ofSeconds(30));

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("加锁失败返回 null")
    void 加锁失败() {
        when(valueOperations.setIfAbsent(eq("k"), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(lock.tryLock("k", Duration.ofSeconds(30))).isNull();
    }

    @Test
    @DisplayName("加锁时带上过期时间")
    void 加锁带过期时间() {
        when(valueOperations.setIfAbsent(eq("k"), anyString(), any(Duration.class))).thenReturn(true);

        lock.tryLock("k", Duration.ofSeconds(45));

        verify(valueOperations).setIfAbsent(eq("k"), anyString(), eq(Duration.ofSeconds(45)));
    }

    @Test
    @DisplayName("每次加锁的令牌都不同")
    void 令牌唯一() {
        when(valueOperations.setIfAbsent(eq("k"), anyString(), any(Duration.class))).thenReturn(true);

        String first = lock.tryLock("k", Duration.ofSeconds(30));
        String second = lock.tryLock("k", Duration.ofSeconds(30));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("解锁成功")
    void 解锁成功() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        assertThat(lock.unlock("k", "token")).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("令牌不匹配时解锁失败")
    void 令牌不匹配() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        assertThat(lock.unlock("k", "别人的令牌")).isFalse();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    @DisplayName("空令牌不解锁，也不访问 Redis")
    void 空令牌不解锁() {
        assertThat(lock.unlock("k", null)).isFalse();
        assertThat(lock.unlock("k", "  ")).isFalse();

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("查询锁状态")
    void 查询锁状态() {
        when(redisTemplate.hasKey("k")).thenReturn(true);
        assertThat(lock.isLocked("k")).isTrue();

        when(redisTemplate.hasKey("k")).thenReturn(false);
        assertThat(lock.isLocked("k")).isFalse();
    }
}
