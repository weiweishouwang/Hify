package com.hify.shared.resilience;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.http.LlmErrorKind;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * LLM 调用的重试规则。
 *
 * <p>按失败类型决定要不要重试、重试几次、每次隔多久：
 *
 * <table border="1">
 *   <caption>重试规则</caption>
 *   <tr><th>失败类型</th><th>重试</th><th>间隔</th></tr>
 *   <tr><td>网络超时</td><td>2 次</td><td>固定 1 秒</td></tr>
 *   <tr><td>限流（429）</td><td>2 次</td><td>退避 2 秒 → 4 秒</td></tr>
 *   <tr><td>认证失败（401/403）</td><td>不重试</td><td>—</td></tr>
 * </table>
 *
 * <p>认证失败不重试：换了把 key 再试也一样会失败，重试只是白等。
 * 限流用退避而不是固定间隔：上游让我们等多久是它说了算，固定间隔容易一头撞上。
 *
 * <p><b>上游返回 5xx 不重试</b>（{@link LlmErrorKind#UPSTREAM_ERROR}）——
 * 失败率交给熔断器判断，连续 5xx 由熔断器把整个 provider 断开，而不是靠重试硬扛。
 */
public class LlmRetryPolicy {

    /** 网络超时：最多尝试 3 次（首次 + 2 次重试）。 */
    static final int TIMEOUT_MAX_ATTEMPTS = 3;

    /** 网络超时的重试间隔：固定 1 秒。 */
    static final Duration TIMEOUT_INTERVAL = Duration.ofSeconds(1);

    /** 限流：最多尝试 3 次（首次 + 2 次重试）。 */
    static final int RATE_LIMITED_MAX_ATTEMPTS = 3;

    /** 限流的第一次退避：2 秒。 */
    static final Duration RATE_LIMITED_INITIAL_INTERVAL = Duration.ofSeconds(2);

    /** 限流的第二次退避：4 秒。 */
    static final Duration RATE_LIMITED_MAX_INTERVAL = Duration.ofSeconds(4);

    private final Sleeper sleeper;

    /** 用真的 {@link Thread#sleep(long)} 等待。 */
    public LlmRetryPolicy() {
        this(Sleeper.thread());
    }

    /**
     * 指定等待方式。测试用假的 {@link Sleeper}，不用真等几秒钟。
     *
     * @param sleeper 等待实现
     */
    public LlmRetryPolicy(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * 按失败类型给出重试计划。
     *
     * @param kind 失败分类
     * @return 重试计划；不重试时是 {@link RetryPlan#NO_RETRY}
     */
    public RetryPlan planFor(LlmErrorKind kind) {
        return switch (kind) {
            case TIMEOUT -> new RetryPlan(TIMEOUT_MAX_ATTEMPTS,
                    List.of(TIMEOUT_INTERVAL, TIMEOUT_INTERVAL));
            case RATE_LIMITED -> new RetryPlan(RATE_LIMITED_MAX_ATTEMPTS,
                    List.of(RATE_LIMITED_INITIAL_INTERVAL, RATE_LIMITED_MAX_INTERVAL));
            case AUTH_FAILED, UPSTREAM_ERROR -> RetryPlan.NO_RETRY;
        };
    }

    /**
     * 执行一次调用，失败时按规则重试。
     *
     * <p>只有 {@link LlmApiException} 会走重试；别的异常直接往外抛。
     *
     * @param action 要执行的调用
     * @param <T>    返回值类型
     * @return 调用结果
     * @throws LlmApiException 重试耗尽仍失败，或失败类型不重试
     */
    public <T> T execute(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (LlmApiException e) {
                RetryPlan plan = planFor(e.getKind());
                if (attempt >= plan.maxAttempts()) {
                    throw e;
                }
                sleep(plan.intervalBeforeAttempt(attempt));
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            // 恢复中断标志，再把异常抛出去（CLAUDE.md §3.1 C5）
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "等待重试时被中断", e);
        }
    }

    /**
     * 一次失败对应的重试计划。
     *
     * @param maxAttempts 总共最多尝试几次（含首次）
     * @param intervals   每次重试前的等待时长，长度必须是 {@code maxAttempts - 1}
     */
    public record RetryPlan(int maxAttempts, List<Duration> intervals) {

        /** 不重试。 */
        public static final RetryPlan NO_RETRY = new RetryPlan(1, List.of());

        public RetryPlan {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts 至少为 1");
            }
            if (intervals.size() != maxAttempts - 1) {
                throw new IllegalArgumentException(
                        "intervals 的长度必须等于 maxAttempts - 1，实际 " + intervals.size());
            }
        }

        /** 要不要重试。 */
        public boolean shouldRetry() {
            return maxAttempts > 1;
        }

        /**
         * 第 {@code attempt} 次尝试失败后、下一次尝试前的等待时长。
         *
         * @param attempt 已经尝试的次数，从 1 开始
         * @return 等待时长
         */
        public Duration intervalBeforeAttempt(int attempt) {
            return intervals.get(attempt - 1);
        }
    }

    /**
     * 等待。抽成接口是为了测试里不用真等。
     */
    @FunctionalInterface
    public interface Sleeper {

        /**
         * 等待一段时间。
         *
         * @param duration 等待时长
         * @throws InterruptedException 等待被打断
         */
        void sleep(Duration duration) throws InterruptedException;

        /** 真的睡。 */
        static Sleeper thread() {
            return duration -> Thread.sleep(duration.toMillis());
        }
    }
}
