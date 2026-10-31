package com.hify.tool.internal.service;

import com.hify.shared.redis.DistributedLock;
import com.hify.shared.redis.IdempotentGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 目录同步的调度。
 *
 * <p>启动时同步一次（同步执行 —— 内置工具的发现是纯内存操作，很快；将来接入 MCP 时，
 * 它读的是 {@code mcp_server.tool_cache} 的缓存快照，同样不会拖慢启动）。
 * 之后每 10 分钟兜底同步一次。
 *
 * <p><b>多副本下定时任务会重复执行</b> —— 用 Redis 分布式锁 + 时间窗口幂等键去重，
 * 同一时刻只有一个副本真正干活。
 */
@Component
@ConditionalOnProperty(name = "hify.tool.sync.enabled", havingValue = "true", matchIfMissing = true)
public class ToolSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ToolSyncScheduler.class);

    /** 兜底同步间隔：10 分钟。 */
    private static final long INTERVAL_MS = 10 * 60_000L;

    /** 锁与幂等键的存活时间。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 时间窗口长度（秒），与兜底同步间隔一致。 */
    private static final long WINDOW_SECONDS = 600L;

    private static final String LOCK_KEY = "hify:tool-sync:lock";
    private static final String GUARD_PREFIX = "hify:tool-sync:window:";

    private final ToolSyncService syncService;
    private final DistributedLock lock;
    private final IdempotentGuard guard;
    private final Executor executor;

    public ToolSyncScheduler(ToolSyncService syncService, DistributedLock lock, IdempotentGuard guard,
                             @Qualifier("asyncExecutor") Executor executor) {
        this.syncService = syncService;
        this.lock = lock;
        this.guard = guard;
        this.executor = executor;
    }

    /** 启动后同步一次，保证管理台一打开就能看到内置工具。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            var result = syncService.syncAll();
            log.info("工具目录启动同步完成 discovered={} created={} updated={} disabled={}",
                    result.getDiscovered(), result.getCreated(), result.getUpdated(), result.getDisabled());
        } catch (RuntimeException exception) {
            // 同步失败不能拖垮启动；定时任务会兜底重试
            log.error("工具目录启动同步失败", exception);
        }
    }

    /** 兜底同步。调度线程只提交任务，不做实际的发现与落库。 */
    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = 60_000L)
    public void schedule() {
        try {
            executor.execute(this::syncGuarded);
        } catch (RejectedExecutionException exception) {
            log.warn("异步线程池已满或关闭，跳过本次工具目录同步");
        }
    }

    private void syncGuarded() {
        long window = currentWindow();
        String token = null;
        try {
            token = lock.tryLock(LOCK_KEY, TTL);
            if (token == null) {
                return;
            }
            if (!guard.tryAcquire(GUARD_PREFIX + window, TTL)) {
                return;
            }
            var result = syncService.syncAll();
            log.info("工具目录同步完成 discovered={} created={} updated={} disabled={}",
                    result.getDiscovered(), result.getCreated(), result.getUpdated(), result.getDisabled());
        } catch (RuntimeException exception) {
            log.error("工具目录同步失败", exception);
        } finally {
            if (token != null) {
                try {
                    lock.unlock(LOCK_KEY, token);
                } catch (RuntimeException exception) {
                    log.error("工具目录同步释放锁失败", exception);
                }
            }
        }
    }

    private long currentWindow() {
        return System.currentTimeMillis() / 1000L / WINDOW_SECONDS;
    }
}
