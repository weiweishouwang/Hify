package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthObservation;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.http.LlmApiException;
import com.hify.shared.redis.DistributedLock;
import com.hify.shared.redis.IdempotentGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/** 调度线程仅提交任务，扫描、Redis 协调、HTTP 探测及持久化全部在 asyncExecutor 执行。 */
@Component
@ConditionalOnProperty(name = "hify.ai.provider-health.enabled", havingValue = "true", matchIfMissing = true)
public class ProviderHealthScheduler {
    private static final Logger log = LoggerFactory.getLogger(ProviderHealthScheduler.class);
    private static final long INTERVAL_MS = 60_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final int BATCH_SIZE = 100;
    private static final Duration LOCK_TTL = Duration.ofMinutes(3);
    private static final Duration WINDOW_TTL = Duration.ofMinutes(3);
    private static final String KEY_PREFIX = "hify:provider-health:";
    private final ModelProviderMapper providers;
    private final ProviderConnectivityService connectivity;
    private final ProviderHealthWriter writer;
    private final DistributedLock lock;
    private final IdempotentGuard guard;
    private final Executor executor;
    private final Clock clock;

    public ProviderHealthScheduler(ModelProviderMapper providers, ProviderConnectivityService connectivity,
                                   ProviderHealthWriter writer, DistributedLock lock, IdempotentGuard guard,
                                   @Qualifier(ThreadPoolConfig.ASYNC_EXECUTOR) Executor executor,
                                   @Qualifier("providerHealthClock") Clock clock) {
        this.providers = providers;
        this.connectivity = connectivity;
        this.writer = writer;
        this.lock = lock;
        this.guard = guard;
        this.executor = executor;
        this.clock = clock;
    }

    @Scheduled(fixedRate = INTERVAL_MS)
    public void schedule() {
        long window = currentWindow();
        submit(() -> scan(window));
    }

    private void scan(long window) {
        try {
            long lastId = 0;
            while (window == currentWindow()) {
                List<ModelProviderDO> page = providers.selectList(new QueryWrapper<ModelProviderDO>()
                        .select("id").eq("enabled", true).gt("id", lastId).orderByAsc("id")
                        .last("LIMIT " + BATCH_SIZE));
                for (ModelProviderDO provider : page) {
                    Long id = provider.getId();
                    submit(() -> probe(id, window));
                }
                if (page.size() < BATCH_SIZE) {
                    return;
                }
                lastId = page.getLast().getId();
            }
        } catch (RuntimeException exception) {
            log.error("供应商健康扫描失败", exception);
        }
    }

    private void probe(Long providerId, long window) {
        if (window != currentWindow()) {
            return;
        }
        String key = KEY_PREFIX + "lock:" + providerId;
        String token = null;
        try {
            token = lock.tryLock(key, LOCK_TTL);
            if (token == null) {
                return;
            }
            if (!guard.tryAcquire(KEY_PREFIX + "window:" + window + ":" + providerId, WINDOW_TTL)) {
                return;
            }
            var provider = providers.selectById(providerId);
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())
                    || !Integer.valueOf(0).equals(provider.getDeleted())) {
                return;
            }
            var observation = observe(provider);
            if (!Thread.currentThread().isInterrupted()) {
                writer.record(observation);
            }
        } catch (RuntimeException exception) {
            // Redis/DB 故障不是上游故障，不能因此增加供应商失败次数。
            log.error("供应商健康任务未完成 providerId={}", providerId, exception);
        } finally {
            if (token != null) {
                try {
                    lock.unlock(key, token);
                } catch (RuntimeException exception) {
                    log.error("供应商健康任务释放锁失败 providerId={}", providerId, exception);
                }
            }
        }
    }

    private ProviderHealthObservation observe(ModelProviderDO provider) {
        LocalDateTime startedAt = now();
        long startNanos = System.nanoTime();
        try {
            connectivity.testConnection(provider);
            return observation(provider, startedAt, startNanos, true, null, null);
        } catch (LlmApiException exception) {
            String message = switch (exception.getKind()) {
                case TIMEOUT -> "供应商请求超时";
                case AUTH_FAILED -> "供应商鉴权失败";
                case RATE_LIMITED -> "供应商请求被限流";
                case UPSTREAM_ERROR -> "供应商连接失败或响应无效";
            };
            return observation(provider, startedAt, startNanos, false, exception.getKind().name(), message);
        } catch (BizException exception) {
            return observation(provider, startedAt, startNanos, false, "CONFIG_ERROR", "供应商连接或鉴权配置无效");
        } catch (RuntimeException exception) {
            // 失败消息使用固定文案，不能把含凭据或响应体的异常原文写进健康表。
            return observation(provider, startedAt, startNanos, false, "PROBE_ERROR", "供应商连通性测试失败");
        }
    }

    private ProviderHealthObservation observation(ModelProviderDO provider, LocalDateTime startedAt,
                                                   long startNanos, boolean success, String code, String message) {
        return new ProviderHealthObservation(provider.getId(), provider.getConfigVersion(), startedAt, now(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos), success, code, message);
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            log.warn("异步线程池已满或关闭，跳过本次健康任务，等待下一分钟");
        }
    }

    private long currentWindow() {
        return clock.instant().getEpochSecond() / SECONDS_PER_MINUTE;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }
}
