package com.hify.tool.internal.controller;

import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.context.CurrentUserHolder;
import com.hify.shared.context.TraceContext;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.Result;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolDefinitionService;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolInvokeResp;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 手工测试工具的 MVC 异步适配器。
 *
 * <p>工具调用是<b>阻塞</b>的（要等上游返回），不能占住 Tomcat 工作线程
 * （CLAUDE.md §3.5）。这里提交到 {@code llmExecutor}，用 {@link DeferredResult}
 * 把结果异步写回响应。
 *
 * <p>业务检查仍在 service 里，控制器不需要了解线程池细节。
 */
@Component
public class ToolInvokeDispatcher {

    /**
     * HTTP 层的等待上限：130 秒。
     *
     * <p>工具自身的超时上限是 120 秒，这里留 10 秒余量。
     */
    private static final long REQUEST_TIMEOUT_MS = 130_000L;

    private final ToolDefinitionService definitionService;
    private final ToolCatalog catalog;
    private final ThreadPoolExecutor executor;

    public ToolInvokeDispatcher(ToolDefinitionService definitionService, ToolCatalog catalog,
                                @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor executor) {
        this.definitionService = definitionService;
        this.catalog = catalog;
        this.executor = executor;
    }

    /**
     * 测试一个工具。
     *
     * @param publicId 工具的对外 ID
     * @param args     模型给出的入参
     * @return 异步结果
     */
    public DeferredResult<Result<ToolInvokeResp>> test(String publicId, Map<String, Object> args) {
        var result = new DeferredResult<Result<ToolInvokeResp>>(REQUEST_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(new BizException(ErrorCode.UPSTREAM_TIMEOUT, "工具调用超时")));

        // 用户与 traceId 必须在调用线程取：线程池会复用线程，ThreadLocal 里可能是别人的值
        Long userId = CurrentUserHolder.currentUserId();
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
                    result.setResult(Result.ok(execute(publicId, args, userId, traceId)));
                } catch (RuntimeException exception) {
                    // 交回统一异常处理器，不把错误包装成成功响应。
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

    private ToolInvokeResp execute(String publicId, Map<String, Object> args, Long userId, String traceId) {
        var tool = definitionService.get(publicId);
        long startNanos = System.nanoTime();
        var context = ToolInvokeContext.of(userId, traceId);
        var outcome = catalog.invoke(context, tool.getSourceType(), tool.getToolKey(), args);

        ToolInvokeResp response = new ToolInvokeResp();
        response.setSuccess(outcome.success());
        response.setContent(outcome.content());
        response.setErrorMessage(outcome.errorMessage());
        response.setMetadata(outcome.metadata());
        response.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        return response;
    }

    private static BizException busy() {
        return new BizException(ErrorCode.TOO_MANY_REQUESTS, "工具调用线程池繁忙，请稍后重试");
    }
}
