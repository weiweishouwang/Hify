package com.hify.ai.internal.controller;

import com.hify.ai.api.ProviderConnectionTestResp;
import com.hify.ai.api.ProviderService;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.web.Result;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** 手动测试的 MVC 异步适配器，业务检查仍在 ProviderService，控制器无需了解线程池细节。 */
@Component
public class ProviderConnectionTestDispatcher {
    /** 覆盖现有连接5秒、读60秒超时，且不改变其他 MVC/SSE 请求的全局超时。 */
    private static final long REQUEST_TIMEOUT_MS = 70_000L;
    private final ProviderService service;
    private final ThreadPoolExecutor executor;

    public ProviderConnectionTestDispatcher(ProviderService service,
            @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    public DeferredResult<Result<ProviderConnectionTestResp>> testConnection(String publicId) {
        var result = new DeferredResult<Result<ProviderConnectionTestResp>>(REQUEST_TIMEOUT_MS);
        result.onTimeout(() -> result.setErrorResult(new BizException(ErrorCode.UPSTREAM_TIMEOUT, "连通性测试超时")));
        Thread caller = Thread.currentThread();
        try {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("LLM 执行器已关闭");
            }
            executor.execute(() -> {
                if (result.isSetOrExpired()) {
                    return;
                }
                // llmExecutor 沿用 CallerRunsPolicy；饱和时不能在 Tomcat 线程做慢请求。
                if (Thread.currentThread() == caller) {
                    result.setErrorResult(busy());
                    return;
                }
                try {
                    result.setResult(Result.ok(service.testConnection(publicId)));
                } catch (RuntimeException exception) {
                    // 交回统一异常处理器，不把错误包装成成功响应。
                    result.setErrorResult(exception);
                }
            });
            // CallerRunsPolicy 在 shutdown 竞态下可能静默丢任务，不能让 HTTP 一直等待。
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
