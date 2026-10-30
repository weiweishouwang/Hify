package com.hify.shared.resilience;

import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.util.function.Supplier;

/**
 * 按 provider 独立的熔断器。
 *
 * <p>每个 provider（openai / anthropic / ollama …）一个熔断器实例：
 * 某个 provider 连续出问题就单独把它断开，不影响其他 provider。
 *
 * <p><b>熔断在外、重试在内</b>：熔断器打开时直接快速失败，不会把请求丢进去重试
 * —— 熔断器已经判定这个 provider 不该再试了，重试只是白等。反过来，一次逻辑调用
 * 内部重试了 3 次仍失败，在熔断器那里只记 1 次失败（失败率算的是逻辑调用，不是 HTTP 尝试）。
 *
 * <p>失败类型里，<b>鉴权失败与限流不算 provider 的故障</b>：
 * 429 是「你太频繁」不是「我坏了」，401 是「你的 key 不对」也不是「我坏了」，
 * 算进失败率会误开熔断，把一个好好的 provider 断掉。
 */
public class CircuitBreakerService {

    private final CircuitBreakerRegistry registry;
    private final LlmRetryPolicy retryPolicy;

    /**
     * @param properties  熔断器参数
     * @param retryPolicy 重试规则
     */
    public CircuitBreakerService(CircuitBreakerProperties properties, LlmRetryPolicy retryPolicy) {
        this.registry = CircuitBreakerRegistry.of(buildConfig(properties));
        this.retryPolicy = retryPolicy;
    }

    /**
     * 按 providerName 取熔断器，没有就建一个。
     *
     * <p>同名拿到的是同一个实例（熔断状态是跨调用累积的，必须共享）。
     *
     * @param providerName provider 标识，如 {@code openai}
     * @return 该 provider 的熔断器
     */
    public CircuitBreaker getOrCreate(String providerName) {
        return registry.circuitBreaker(providerName);
    }

    /**
     * 执行一次逻辑调用：熔断在外、重试在内。
     *
     * @param providerName provider 标识
     * @param action       要执行的调用
     * @param <T>          返回值类型
     * @return 调用结果
     * @throws LlmApiException 调用失败（含重试耗尽），或熔断打开被拒
     */
    public <T> T execute(String providerName, Supplier<T> action) {
        CircuitBreaker breaker = getOrCreate(providerName);
        try {
            return breaker.executeSupplier(() -> retryPolicy.execute(action));
        } catch (CallNotPermittedException e) {
            // 熔断打开：请求根本没发出去，按上游不可用处理
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR,
                    "熔断已打开，拒绝调用: " + providerName, e);
        }
    }

    /** 按配置装配熔断器。 */
    private static CircuitBreakerConfig buildConfig(CircuitBreakerProperties properties) {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.getSlidingWindowSize())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedNumberOfCallsInHalfOpenState())
                .ignoreException(CircuitBreakerService::isIgnorable)
                .build();
    }

    /**
     * 鉴权失败与限流不算 provider 的故障，不计入失败率。
     *
     * @param throwable 调用抛出的异常
     * @return true 表示忽略这次失败
     */
    private static boolean isIgnorable(Throwable throwable) {
        if (throwable instanceof LlmApiException e) {
            return e.getKind() == LlmErrorKind.AUTH_FAILED
                    || e.getKind() == LlmErrorKind.RATE_LIMITED;
        }
        return false;
    }
}
