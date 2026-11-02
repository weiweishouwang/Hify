package com.hify.workflow.internal.controller;

import com.hify.shared.api.CurrentUserService;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.context.TraceContext;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.Result;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工作流运行的调度。
 *
 * <p><b>跑在 {@code llmExecutor} 上</b>：工作流里可能有多个模型调用，全是阻塞的
 * （CLAUDE.md §3.5）。controller 用 {@link DeferredResult} 立刻返回，
 * 跑完再把结果塞回去。
 *
 * <p><b>用户 id 在这里（HTTP 线程）解析</b>：执行跑在线程池里，那边的
 * {@code CurrentUserHolder} 是空的（CLAUDE.md §3.1 C5）。
 */
@Component
public class WorkflowRunDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRunDispatcher.class);

    /** 一次运行的最长等待时间。 */
    private static final long RUN_TIMEOUT_MS = 300_000L;

    private final WorkflowService service;
    private final CurrentUserService currentUsers;
    private final ThreadPoolExecutor llmExecutor;

    public WorkflowRunDispatcher(WorkflowService service, CurrentUserService currentUsers,
                                @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor llmExecutor) {
        this.service = service;
        this.currentUsers = currentUsers;
        this.llmExecutor = llmExecutor;
    }

    /**
     * 起一次工作流运行。
     *
     * @param publicId 工作流的对外 ID
     * @param request  运行请求
     * @return 异步结果
     */
    public DeferredResult<Result<WorkflowRunResp>> run(String publicId, WorkflowRunReq request) {
        DeferredResult<Result<WorkflowRunResp>> result = new DeferredResult<>(RUN_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(
                new BizException(ErrorCode.UPSTREAM_TIMEOUT, "工作流执行超时")));
        // 用户 id 在 HTTP 线程上取 —— 编排跑在线程池里，ThreadLocal 那边是空的
        Long userId = currentUsers.currentUserId();
        submit(result, () -> service.run(publicId, request, userId));
        return result;
    }

    private void submit(DeferredResult<Result<WorkflowRunResp>> result,
                        java.util.function.Supplier<WorkflowRunResp> action) {
        String traceId = TraceContext.get();
        try {
            if (llmExecutor.isShutdown()) {
                throw new RejectedExecutionException("LLM 执行器已关闭");
            }
            llmExecutor.execute(() -> {
                if (result.isSetOrExpired()) {
                    return;
                }
                MDC.put(TraceContext.MDC_KEY, traceId);
                try {
                    result.setResult(Result.ok(action.get()));
                } catch (RuntimeException exception) {
                    log.warn("工作流运行失败 reason={}", exception.getClass().getSimpleName());
                    result.setErrorResult(exception);
                } finally {
                    MDC.remove(TraceContext.MDC_KEY);
                }
            });
        } catch (RejectedExecutionException exception) {
            result.setErrorResult(new BizException(ErrorCode.TOO_MANY_REQUESTS, "模型调用繁忙，请稍后重试"));
        }
    }
}
