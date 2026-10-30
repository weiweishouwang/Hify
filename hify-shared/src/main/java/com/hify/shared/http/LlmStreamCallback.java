package com.hify.shared.http;

/**
 * 上游流式响应的回调。
 *
 * <p>上游每发来一条 SSE 事件就回调一次，调用方据此把增量内容推给下游
 * （对话引擎的 {@code Flux<LlmChunk>}、HTTP 层的 SSE 写出等）。
 *
 * <p>回调抛出的异常会原样向上抛给 {@link LlmHttpClient#stream} 的调用方——
 * 那是调用方自己的问题，不是上游故障，所以不包成 {@link LlmApiException}。
 */
@FunctionalInterface
public interface LlmStreamCallback {

    /**
     * 收到一条 SSE 事件时调用。
     *
     * @param type 事件类型，对应 SSE 的 {@code event:} 字段；上游没指定时为 {@code null}
     *              （OpenAI 兼容协议就是这种形态，Anthropic 会给 {@code content_block_delta} 这类值）
     * @param data 事件数据，对应 SSE 的 {@code data:} 字段。一条事件里的多行 data 已按 {@code \n} 拼接；
     *              心跳注释行（{@code : ping}）被过滤，不会触发回调
     */
    void onEvent(String type, String data);
}
