package com.hify.shared.log;

import com.hify.shared.context.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求日志拦截器。
 *
 * <p>做两件事：
 * <ul>
 *   <li>请求入口生成 traceId 写进 MDC，请求结束清理 —— 这样每条日志都带着同一个
 *       traceId，可以把一次请求里的所有日志串起来（CLAUDE.md §3.1 C5：线程池会复用
 *       线程，不清理就会把上一个请求的 traceId 带过来）</li>
 *   <li>请求结束时记一条日志：method、path、status、耗时。耗时超过 1 秒记 WARN</li>
 * </ul>
 *
 * <p><b>traceId 优先用 OpenTelemetry 的</b>：有 OTel agent 或 SDK 时，
 * {@link Span#current()} 能拿到当前 span，日志里的 traceId 就是 OTel 的 trace id，
 * 跟链路追踪对得上；没有 OTel 时退回自己生成的 32 位十六进制 id。
 *
 * <p>耗时超过 1 秒记 WARN 是给「慢请求」一个可见信号：Tomcat 线程被占住的时长
 * 直接等于这个耗时。
 */
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLogInterceptor.class);

    /** 慢请求阈值：1 秒。 */
    static final long SLOW_REQUEST_MILLIS = 1000L;

    /** 存放开始时间的 request 属性名。包级可见，测试里可以塞一个更早的时间来模拟慢请求。 */
    static final String START_TIME_ATTRIBUTE = RequestLogInterceptor.class.getName() + ".startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        resolveTrace();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            long cost = elapsedMillis(request);
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();

            if (cost > SLOW_REQUEST_MILLIS) {
                log.warn("慢请求 method={} path={} status={} cost={}ms", method, path, status, cost);
            } else {
                log.info("请求完成 method={} path={} status={} cost={}ms", method, path, status, cost);
            }
        } finally {
            // 一定要清：Tomcat 线程会被下一个请求复用
            TraceContext.clear();
        }
    }

    /**
     * 取当前 traceId：有 OTel 的 span 就用它的，没有就自己生成一个。
     *
     * <p>有 OTel 时 spanId 也一起写进 MDC，生产环境的 JSON 日志里会带上它，
     * 日志与链路追踪能精确对上。
     */
    private static void resolveTrace() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            TraceContext.set(spanContext.getTraceId(), spanContext.getSpanId());
        } else {
            TraceContext.set(TraceContext.newTraceId());
        }
    }

    /** 从请求里取开始时间算耗时；取不到（没走过 preHandle）按 0 算。 */
    private static long elapsedMillis(HttpServletRequest request) {
        Object start = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(start instanceof Long startNanos)) {
            return 0L;
        }
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
