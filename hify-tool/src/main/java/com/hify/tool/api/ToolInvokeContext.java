package com.hify.tool.api;

/**
 * 工具调用的上下文。
 *
 * <p><b>显式携带，不靠 ThreadLocal</b>（code-organization spec §M7）：工具调用会跨线程池，
 * ThreadLocal 里残留的值会被下一次调用读到。
 *
 * @param userId  调用者用户 id，未登录时为 null
 * @param traceId 链路追踪 id
 */
public record ToolInvokeContext(Long userId, String traceId) {

    public static ToolInvokeContext of(Long userId, String traceId) {
        return new ToolInvokeContext(userId, traceId);
    }
}
