package com.hify.mcp.internal.controller;

import com.hify.mcp.api.McpConnectionTestResp;
import com.hify.mcp.api.McpServerService;
import com.hify.mcp.api.McpToolSyncResp;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.context.TraceContext;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.Result;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * MCP 连接测试与工具同步的 MVC 异步适配器。
 *
 * <p>这两个动作都会发起上游调用（stdio 要 spawn 进程、http 要 initialize），
 * 是<b>阻塞</b>的 —— 不能占住 Tomcat 工作线程（CLAUDE.md §3.5）。
 */
@Component
public class McpServerDispatcher {

    /** HTTP 层的等待上限：一次同步可能要连多个服务。 */
    private static final long REQUEST_TIMEOUT_MS = 120_000L;

    private final McpServerService service;
    private final ThreadPoolExecutor executor;

    public McpServerDispatcher(McpServerService service,
                               @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    /** 连接测试。 */
    public DeferredResult<Result<McpConnectionTestResp>> test(String publicId) {
        var result = new DeferredResult<Result<McpConnectionTestResp>>(REQUEST_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(new BizException(ErrorCode.UPSTREAM_TIMEOUT, "连接测试超时")));
        submit(result, () -> service.test(publicId));
        return result;
    }

    /** 工具同步。 */
    public DeferredResult<Result<McpToolSyncResp>> sync() {
        var result = new DeferredResult<Result<McpToolSyncResp>>(REQUEST_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(new BizException(ErrorCode.UPSTREAM_TIMEOUT, "工具同步超时")));
        submit(result, service::syncTools);
        return result;
    }

    private <T> void submit(DeferredResult<Result<T>> result, java.util.function.Supplier<T> action) {
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
                    result.setResult(Result.ok(action.get()));
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
    }

    private static BizException busy() {
        return new BizException(ErrorCode.TOO_MANY_REQUESTS, "模型调用线程池繁忙，请稍后重试");
    }
}
