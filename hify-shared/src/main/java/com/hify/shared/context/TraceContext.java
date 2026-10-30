package com.hify.shared.context;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 的线程上下文。
 *
 * <p>traceId 同时写入 SLF4J 的 MDC，这样日志里自动带上它；也保存在 ThreadLocal 里，
 * 供跨模块显式传递。
 *
 * <p>**同样只在 Web 入口设置，模块之间显式传递。** 线程池会复用线程，
 * 不清理就会把上一个请求的 traceId 带到下一个请求的日志里
 * （见 CLAUDE.md §3.1 C5）。
 */
public final class TraceContext {

    /** MDC 中存放 traceId 的键名，与日志配置里的 pattern 对应。 */
    public static final String MDC_KEY = "traceId";

    /** MDC 中存放 spanId 的键名。有 OpenTelemetry 时才有值。 */
    public static final String SPAN_ID_KEY = "spanId";

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 生成一个新的 traceId：32 位十六进制（不带连字符的 UUID）。
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 traceId，同时写入 MDC。
     *
     * @param traceId 链路 ID；传 null 等同于清理
     */
    public static void set(String traceId) {
        set(traceId, null);
    }

    /**
     * 设置 traceId 与 spanId，同时写入 MDC。
     *
     * <p>spanId 只在有 OpenTelemetry 时才有值，传 null 表示没有。
     *
     * @param traceId 链路 ID；传 null 等同于清理
     * @param spanId  段 ID，可为 null
     */
    public static void set(String traceId, String spanId) {
        if (traceId == null || traceId.isBlank()) {
            clear();
            return;
        }
        HOLDER.set(traceId);
        MDC.put(MDC_KEY, traceId);
        if (spanId == null || spanId.isBlank()) {
            MDC.remove(SPAN_ID_KEY);
        } else {
            MDC.put(SPAN_ID_KEY, spanId);
        }
    }

    /** 取当前 traceId；未设置时返回 null。 */
    public static String get() {
        return HOLDER.get();
    }

    /** 清理 traceId 与 MDC 中的对应项。Web 层必须在 finally 中调用。 */
    public static void clear() {
        HOLDER.remove();
        MDC.remove(MDC_KEY);
        MDC.remove(SPAN_ID_KEY);
    }
}
