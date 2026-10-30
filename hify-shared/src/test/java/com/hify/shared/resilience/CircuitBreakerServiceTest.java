package com.hify.shared.resilience;

import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CircuitBreakerService} 的单元测试。
 *
 * <p>直接打熔断器（不走重试）来精确控制「调用了几次」；重试与熔断的组合另有专门的用例。
 */
class CircuitBreakerServiceTest {

    private CircuitBreakerProperties properties;
    private CircuitBreakerService service;

    @BeforeEach
    void setUp() {
        properties = new CircuitBreakerProperties();
        // 半开状态的用例不用真等 30 秒
        properties.setWaitDurationInOpenState(Duration.ofMillis(100));
        service = new CircuitBreakerService(properties, new LlmRetryPolicy(duration -> { }));
    }

    private static LlmApiException failure(LlmErrorKind kind) {
        return new LlmApiException(kind, "失败: " + kind);
    }

    /** 直接打熔断器，精确控制调用次数。 */
    private void callBreaker(CircuitBreaker breaker, LlmErrorKind kind, int times) {
        for (int i = 0; i < times; i++) {
            try {
                breaker.executeSupplier(() -> {
                    throw failure(kind);
                });
            } catch (LlmApiException expected) {
                // 这就是要统计的失败
            }
        }
    }

    @Test
    @DisplayName("同一 providerName 拿到同一个熔断器，不同 provider 相互独立")
    void breakersArePerProviderAndReused() {
        CircuitBreaker openai = service.getOrCreate("openai");
        CircuitBreaker anthropic = service.getOrCreate("anthropic");

        assertThat(service.getOrCreate("openai")).isSameAs(openai);
        assertThat(service.getOrCreate("anthropic")).isNotSameAs(openai);
    }

    @Test
    @DisplayName("鉴权失败不计入失败率：连续 10 次也不会打开熔断")
    void authFailureDoesNotCountTowardsFailureRate() {
        CircuitBreaker breaker = service.getOrCreate("openai");

        callBreaker(breaker, LlmErrorKind.AUTH_FAILED, 10);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("限流不计入失败率：连续 10 次也不会打开熔断")
    void rateLimitedDoesNotCountTowardsFailureRate() {
        CircuitBreaker breaker = service.getOrCreate("openai");

        callBreaker(breaker, LlmErrorKind.RATE_LIMITED, 10);

        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("失败率超过阈值就打开熔断")
    void opensWhenFailureRateExceedsThreshold() {
        CircuitBreaker breaker = service.getOrCreate("openai");

        // 窗口 10 次，其中 6 次超时 → 失败率 60% > 50%
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 6);
        for (int i = 0; i < 4; i++) {
            breaker.executeSupplier(() -> "ok");
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("失败率没到阈值就保持闭合")
    void staysClosedBelowThreshold() {
        CircuitBreaker breaker = service.getOrCreate("openai");

        // 窗口 10 次，其中 4 次超时 → 失败率 40% < 50%
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 4);
        for (int i = 0; i < 6; i++) {
            breaker.executeSupplier(() -> "ok");
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("熔断打开后调用被拒，转成 LlmApiException")
    void rejectsCallsWhenOpen() {
        CircuitBreaker breaker = service.getOrCreate("openai");
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 6);
        for (int i = 0; i < 4; i++) {
            breaker.executeSupplier(() -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> service.execute("openai", () -> "ok"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.UPSTREAM_ERROR);

        // 被拒的调用不会真的发出去
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("等过开放时长后进半开，试探调用成功就回到闭合")
    void halfOpenAfterWaitDuration() throws InterruptedException {
        CircuitBreaker breaker = service.getOrCreate("openai");
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 6);
        for (int i = 0; i < 4; i++) {
            breaker.executeSupplier(() -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(200);

        // 第一次试探：触发进入半开
        breaker.executeSupplier(() -> "ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        // 半开允许 3 次试探，都成功后回到闭合
        breaker.executeSupplier(() -> "ok");
        breaker.executeSupplier(() -> "ok");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("熔断在外、重试在内：一次逻辑调用内部重试 3 次，熔断器只记 1 次失败")
    void retryHappensInsideCircuitBreaker() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> service.execute("openai", () -> {
            attempts.incrementAndGet();
            throw failure(LlmErrorKind.TIMEOUT);
        })).isInstanceOf(LlmApiException.class);

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(service.getOrCreate("openai").getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("熔断打开时直接快速失败，不触发重试")
    void openCircuitFailsFastWithoutRetry() {
        CircuitBreaker breaker = service.getOrCreate("openai");
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 6);
        for (int i = 0; i < 4; i++) {
            breaker.executeSupplier(() -> "ok");
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> service.execute("openai", () -> {
            attempts.incrementAndGet();
            return "ok";
        })).isInstanceOf(LlmApiException.class);

        assertThat(attempts.get()).isZero();
    }

    @Test
    @DisplayName("熔断器直接调用时抛的是 CallNotPermittedException")
    void rawBreakerThrowsCallNotPermitted() {
        CircuitBreaker breaker = service.getOrCreate("openai");
        callBreaker(breaker, LlmErrorKind.TIMEOUT, 6);
        for (int i = 0; i < 4; i++) {
            breaker.executeSupplier(() -> "ok");
        }

        assertThatThrownBy(() -> breaker.executeSupplier(() -> "ok"))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
