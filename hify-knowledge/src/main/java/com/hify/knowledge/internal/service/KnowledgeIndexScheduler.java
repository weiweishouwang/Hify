package com.hify.knowledge.internal.service;

import com.hify.knowledge.internal.entity.DocumentDO;
import com.hify.shared.redis.DistributedLock;
import com.hify.shared.redis.IdempotentGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 索引与删除的兜底任务。
 *
 * <p>两库之间没有分布式事务（CLAUDE.md §3.3），任何一次写一半的崩溃都必须能安全重跑：
 * <ul>
 *   <li>卡在 {@code indexing} 超过 10 分钟的文档 → 重新索引</li>
 *   <li>处于 {@code deleting} 的文档 → 继续删 PG，硬删 MySQL 行</li>
 * </ul>
 *
 * <p><b>多副本下定时任务会重复执行</b> —— 用 Redis 分布式锁 + 时间窗口幂等键去重。
 */
@Component
@ConditionalOnProperty(name = "hify.knowledge.index.enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeIndexScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexScheduler.class);

    /** 兜底扫描间隔：5 分钟。 */
    private static final long INTERVAL_MS = 5 * 60_000L;

    /** 索引卡住多久算异常（分钟）。 */
    private static final int STUCK_MINUTES = 10;

    /** 单轮最多处理多少个文档。 */
    private static final int BATCH_SIZE = 20;

    /** 锁与幂等键的存活时间。 */
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 时间窗口长度（秒），与扫描间隔一致。 */
    private static final long WINDOW_SECONDS = 300L;

    private static final String LOCK_KEY = "hify:knowledge-index:lock";
    private static final String GUARD_PREFIX = "hify:knowledge-index:window:";

    private final IndexingService indexingService;
    private final DocumentServiceImpl documentService;
    private final DistributedLock lock;
    private final IdempotentGuard guard;
    private final Executor executor;

    public KnowledgeIndexScheduler(IndexingService indexingService, DocumentServiceImpl documentService,
                                  DistributedLock lock, IdempotentGuard guard,
                                  @Qualifier("asyncExecutor") Executor executor) {
        this.indexingService = indexingService;
        this.documentService = documentService;
        this.lock = lock;
        this.guard = guard;
        this.executor = executor;
    }

    /** 调度线程只提交任务，不做实际的索引与清理。 */
    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = 60_000L)
    public void schedule() {
        try {
            executor.execute(this::runGuarded);
        } catch (RejectedExecutionException exception) {
            log.warn("异步线程池已满或关闭，跳过本次知识库兜底任务");
        }
    }

    private void runGuarded() {
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
            retryStuckIndexing();
            finishPendingDeletion();
        } catch (RuntimeException exception) {
            log.error("知识库兜底任务失败", exception);
        } finally {
            if (token != null) {
                try {
                    lock.unlock(LOCK_KEY, token);
                } catch (RuntimeException exception) {
                    log.error("知识库兜底任务释放锁失败", exception);
                }
            }
        }
    }

    /** 重新索引没索引成的文档（任务丢了或中途挂了）。 */
    private void retryStuckIndexing() {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(STUCK_MINUTES);
        List<DocumentDO> stuck = indexingService.listPendingIndexing(before, BATCH_SIZE);
        for (DocumentDO document : stuck) {
            log.info("重新索引卡住的文档 documentId={}", document.getId());
            indexingService.indexNow(document.getId());
        }
    }

    /** 继续删 PG 里残留的分段，再硬删 MySQL 行。 */
    private void finishPendingDeletion() {
        for (DocumentDO document : indexingService.listDeleting(BATCH_SIZE)) {
            try {
                documentService.finishCleanup(document.getId(), document.getKbId());
                log.info("补完文档删除 documentId={}", document.getId());
            } catch (RuntimeException exception) {
                log.warn("补完文档删除失败 documentId={} reason={}",
                        document.getId(), exception.getClass().getSimpleName());
            }
        }
    }

    private long currentWindow() {
        return System.currentTimeMillis() / 1000L / WINDOW_SECONDS;
    }
}
