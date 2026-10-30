package com.hify.shared.http;

import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;

/**
 * 从 SSE 响应体里逐帧读出事件。
 *
 * <p>自己解析而不是用 {@code okhttp-sse}，有两个原因：
 * <ul>
 *   <li>{@code okhttp-sse} 4.12.0 的同步入口 {@code EventSources.processResponse} 是坏的——
 *       它构造的 {@code RealEventSource} 没有初始化 {@code call}（lateinit），一调用就抛
 *       {@code UninitializedPropertyAccessException}</li>
 *   <li>它剩下的入口是异步的（{@code newEventSource}），回调跑在 OkHttp 的调度线程上，
 *       调用方想「阻塞到流结束」就得自己加闩锁；而且异步请求受 OkHttp
 *       {@code Dispatcher} 的 {@code maxRequestsPerHost}（默认 5）限制，
 *       同一个 provider 主机上最多只能挂 5 条并发 SSE</li>
 * </ul>
 * 直接读响应体则全程留在调用方线程上：异常自然往外抛，回调跑在调用方的线程上，
 * 并发数也不受 Dispatcher 限制。
 *
 * <p>帧格式按 <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html">SSE 规范</a>
 * 处理：空行是事件边界，{@code event:} 给类型、{@code data:} 给内容，
 * 多行 {@code data:} 用 {@code \n} 拼接，{@code :} 开头的是注释（心跳）直接忽略。
 */
final class SseFrameReader {

    private SseFrameReader() {
    }

    /**
     * 读完整个响应体，把每条事件回调出去。
     *
     * @param response  上游响应，状态码与 Content-Type 已校验过
     * @param callback  事件回调；回调抛出的异常原样往外传
     * @throws IOException 读响应体失败（连接断开、读超时）
     */
    static void readAll(Response response, LlmStreamCallback callback) throws IOException {
        if (response.body() == null) {
            throw new IOException("SSE 响应没有响应体");
        }
        BufferedSource source = response.body().source();

        String eventType = null;
        StringBuilder data = new StringBuilder();
        boolean hasData = false;

        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                // 流结束。最后一条事件没有以空行收尾，按规范丢弃
                return;
            }
            if (line.isEmpty()) {
                // 空行 = 事件边界
                if (hasData || eventType != null) {
                    callback.onEvent(eventType, data.toString());
                }
                eventType = null;
                data.setLength(0);
                hasData = false;
                continue;
            }
            if (line.charAt(0) == ':') {
                // 注释行，通常是心跳
                continue;
            }

            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }

            switch (field) {
                case "event" -> eventType = value;
                case "data" -> {
                    if (hasData) {
                        data.append('\n');
                    }
                    data.append(value);
                    hasData = true;
                }
                default -> {
                    // id / retry 是服务端发给客户端的续传参数，请求上游时用不上
                }
            }
        }
    }
}
