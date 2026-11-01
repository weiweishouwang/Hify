package com.hify.shared.http;

import okhttp3.Response;
import okio.BufferedSource;

import java.io.IOException;

/**
 * 从 NDJSON 响应体里逐行读出 JSON 帧。
 *
 * <p><b>为什么要有第二种分帧策略</b>：SSE 用空行分帧，NDJSON 用换行分帧 ——
 * 两种协议的分帧规则不同，不能共用一套切分逻辑（CLAUDE.md §3.5：
 * 「{@code shared.http.SseFrameReader} 现在只切 SSE 帧，要接 Ollama 得先抽成
 * 「切 SSE 帧」与「按行切 JSON」两种策略」）。
 *
 * <p>Ollama 原生的 {@code /api/chat} 在 {@code stream: true} 时返回
 * {@code Content-Type: application/x-ndjson}：一行一个完整 JSON 对象，
 * 末帧带 {@code done: true}。
 */
final class NdjsonFrameReader {

    private NdjsonFrameReader() {
    }

    /**
     * 读完整个响应体，把每一行回调出去。
     *
     * @param response  上游响应，状态码已校验过
     * @param callback  行回调；回调抛出的异常原样往外传
     * @throws IOException 读响应体失败（连接断开、读超时）
     */
    static void readAll(Response response, LlmStreamCallback callback) throws IOException {
        if (response.body() == null) {
            throw new IOException("NDJSON 响应没有响应体");
        }
        BufferedSource source = response.body().source();
        while (true) {
            String line = source.readUtf8Line();
            if (line == null) {
                return;
            }
            String trimmed = line.trim();
            // 空行是 NDJSON 里的分隔噪声，不是帧
            if (!trimmed.isEmpty()) {
                callback.onEvent(null, trimmed);
            }
        }
    }
}
