package com.hify.knowledge.internal.controller;

import com.hify.knowledge.api.KnowledgeSearchService;
import com.hify.knowledge.api.SearchQuery;
import com.hify.knowledge.api.SearchResult;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.context.CurrentUserHolder;
import com.hify.shared.context.TraceContext;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.Result;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 检索的 MVC 异步适配器。
 *
 * <p>检索是<b>阻塞</b>的（要等上游向量化 + PG 检索），不能占住 Tomcat 工作线程
 * （CLAUDE.md §3.5）。这里提交到 {@code llmExecutor}，用 {@link DeferredResult}
 * 把结果异步写回响应。
 */
@Component
public class KnowledgeSearchDispatcher {

    /** HTTP 层的等待上限：检索含一次向量化，按 70 秒兜底。 */
    private static final long REQUEST_TIMEOUT_MS = 70_000L;

    private final KnowledgeSearchService searchService;
    private final ThreadPoolExecutor executor;

    public KnowledgeSearchDispatcher(KnowledgeSearchService searchService,
                                     @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor executor) {
        this.searchService = searchService;
        this.executor = executor;
    }

    /**
     * 执行一次检索。
     *
     * @param kbPublicId 知识库的对外标识
     * @param query      检索条件
     * @return 异步结果
     */
    public DeferredResult<Result<List<SearchResult>>> search(String kbPublicId, SearchQuery query) {
        var result = new DeferredResult<Result<List<SearchResult>>>(REQUEST_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(new BizException(ErrorCode.UPSTREAM_TIMEOUT, "检索超时")));

        String traceId = TraceContext.get();
        Thread caller = Thread.currentThread();
        try {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("LLM 执行器已关闭");
            }
            executor.execute(() -> {
                if (result.isSetOrExpired()) {
                    return;
                }
                // llmExecutor 沿用 CallerRunsPolicy；饱和时不能在 Tomcat 线程做阻塞调用。
                if (Thread.currentThread() == caller) {
                    result.setErrorResult(busy());
                    return;
                }
                MDC.put(TraceContext.MDC_KEY, traceId);
                try {
                    result.setResult(Result.ok(searchService.search(kbPublicId, query)));
                } catch (RuntimeException exception) {
                    result.setErrorResult(exception);
                } finally {
                    MDC.remove(TraceContext.MDC_KEY);
                }
            });
            if (executor.isShutdown() && !result.isSetOrExpired()) {
                result.setErrorResult(busy());
            }
        } catch (RejectedExecutionException exception) {
            result.setErrorResult(busy());
        }
        return result;
    }

    private static BizException busy() {
        return new BizException(ErrorCode.TOO_MANY_REQUESTS, "模型调用线程池繁忙，请稍后重试");
    }
}
