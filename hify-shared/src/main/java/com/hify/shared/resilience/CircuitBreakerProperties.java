package com.hify.shared.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 熔断器参数。
 *
 * <p>对应 Resilience4j 的 {@code CircuitBreakerConfig}，配置在
 * {@code application.yml} 的 {@code hify.resilience.circuit-breaker} 下。
 *
 * <p>默认值与 yml 里写的保持一致 —— yml 只是把隐含的默认值显式写出来，
 * 少了一份配置也能按同样的参数跑。
 */
@ConfigurationProperties(prefix = "hify.resilience.circuit-breaker")
public class CircuitBreakerProperties {

    /** 滑动窗口大小：统计最近多少次调用。 */
    private int slidingWindowSize = 10;

    /** 失败率阈值（百分比）。窗口内失败率超过它就打开熔断。 */
    private int failureRateThreshold = 50;

    /** 熔断打开后等多久进入半开状态。 */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);

    /** 半开状态允许的试探调用次数，全成功才回到闭合状态。 */
    private int permittedNumberOfCallsInHalfOpenState = 3;

    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(int slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public int getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(int failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public Duration getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    public int getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
    }
}
