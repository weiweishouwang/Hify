package com.hify.shared.resilience;

import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LlmRetryPolicy} 的单元测试。
 *
 * <p>用假的 {@link LlmRetryPolicy.Sleeper} 记录等待时长，不用真等几秒。
 */
class LlmRetryPolicyTest {

    private final List<Duration> slept = new ArrayList<>();
    private final LlmRetryPolicy policy = new LlmRetryPolicy(slept::add);

    @BeforeEach
    void setUp() {
        slept.clear();
    }

    private static LlmApiException failure(LlmErrorKind kind) {
        return new LlmApiException(kind, "失败: " + kind);
    }

    @Test
    @DisplayName("网络超时：重试 2 次，固定间隔 1 秒")
    void timeoutRetriesTwiceWithOneSecondInterval() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw failure(LlmErrorKind.TIMEOUT);
        }))
                .isInstanceOf(LlmApiException.class);

        // 首次 + 2 次重试 = 3 次
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(slept).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("限流：重试 2 次，退避 2 秒再 4 秒")
    void rateLimitedRetriesWithBackoff() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw failure(LlmErrorKind.RATE_LIMITED);
        }))
                .isInstanceOf(LlmApiException.class);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(slept).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("认证失败：不重试")
    void authFailedDoesNotRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw failure(LlmErrorKind.AUTH_FAILED);
        }))
                .isInstanceOf(LlmApiException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    @DisplayName("上游 5xx：不重试，交给熔断器判断")
    void upstreamErrorDoesNotRetry() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw failure(LlmErrorKind.UPSTREAM_ERROR);
        }))
                .isInstanceOf(LlmApiException.class);

        assertThat(attempts.get()).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    @DisplayName("成功：只调一次，不等待")
    void successCallsOnce() {
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.execute(() -> {
            attempts.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(slept).isEmpty();
    }

    @Test
    @DisplayName("中途成功：不再继续重试")
    void stopsRetryingAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw failure(LlmErrorKind.TIMEOUT);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(slept).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("planFor：按失败类型给出重试计划")
    void planForMapsKindsToPlans() {
        assertThat(policy.planFor(LlmErrorKind.TIMEOUT).maxAttempts()).isEqualTo(3);
        assertThat(policy.planFor(LlmErrorKind.TIMEOUT).intervals())
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(policy.planFor(LlmErrorKind.RATE_LIMITED).maxAttempts()).isEqualTo(3);
        assertThat(policy.planFor(LlmErrorKind.RATE_LIMITED).intervals())
                .containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));

        assertThat(policy.planFor(LlmErrorKind.AUTH_FAILED).shouldRetry()).isFalse();
        assertThat(policy.planFor(LlmErrorKind.UPSTREAM_ERROR).shouldRetry()).isFalse();
    }
}
