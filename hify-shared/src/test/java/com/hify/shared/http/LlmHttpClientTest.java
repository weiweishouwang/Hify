package com.hify.shared.http;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LlmHttpClient} 的单元测试。
 *
 * <p>起真的 http 服务（MockWebServer）而不是 mock 掉 HTTP 客户端——
 * 超时、状态码、SSE 分帧这些行为只有真跑一遍才算验过。
 */
class LlmHttpClientTest {

    /** 超时用例专用的客户端：把三档超时都压到几百毫秒，不用真等一分钟。 */
    private static final LlmHttpClient SHORT_TIMEOUT_CLIENT = new LlmHttpClient(
            Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ofMillis(300));

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String url(String path) {
        return server.url(path).toString();
    }

    @Test
    void getSendsHeadersWithoutBodyAndReturnsJson() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{\"data\":[]}"));
        assertThat(new LlmHttpClient().get(url("/v1/models"), Map.of("Authorization", "Bearer test")))
                .isEqualTo("{\"data\":[]}");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/v1/models");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test");
        assertThat(request.getHeader("Content-Type")).isNull();
        assertThat(request.getBodySize()).isZero();
    }

    @Test
    void getPreservesErrorClassificationAndTimeout() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("sensitive-error"));
        assertThatThrownBy(() -> new LlmHttpClient().get(url("/models"), Map.of()))
                .isInstanceOfSatisfying(LlmApiException.class, exception -> {
                    assertThat(exception.getKind()).isEqualTo(LlmErrorKind.AUTH_FAILED);
                    assertThat(exception.getMessage()).doesNotContain("sensitive-error");
                });
        server.enqueue(new MockResponse().setBody("{}").setBodyDelay(1, TimeUnit.SECONDS));
        assertThatThrownBy(() -> SHORT_TIMEOUT_CLIENT.get(url("/models"), Map.of()))
                .isInstanceOfSatisfying(LlmApiException.class,
                        exception -> assertThat(exception.getKind()).isEqualTo(LlmErrorKind.TIMEOUT));
    }

    // ── 普通请求 ────────────────────────────────────────────────────

    @Test
    @DisplayName("post：把请求头与请求体发出去，返回响应体")
    void postSendsRequestAndReturnsBody() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String body = new LlmHttpClient().post(
                url("/v1/chat/completions"),
                Map.of("Authorization", "Bearer sk-test", "X-Trace", "t-1"),
                "{\"model\":\"gpt-x\"}");

        assertThat(body).isEqualTo("{\"ok\":true}");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"model\":\"gpt-x\"}");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");
        assertThat(request.getHeader("X-Trace")).isEqualTo("t-1");
        // 没显式给 Content-Type 时默认 JSON，否则 provider 会拒收
        assertThat(request.getHeader("Content-Type")).contains("application/json");
    }

    @Test
    @DisplayName("post：401 归到 AUTH_FAILED")
    void postUnauthorizedBecomesAuthFailed() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key\"}"));

        assertThatThrownBy(() -> new LlmHttpClient().post(url("/v1/chat"), Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.AUTH_FAILED);
    }

    @Test
    @DisplayName("post：403 同样归到 AUTH_FAILED")
    void postForbiddenBecomesAuthFailed() {
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThatThrownBy(() -> new LlmHttpClient().post(url("/v1/chat"), Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.AUTH_FAILED);
    }

    @Test
    @DisplayName("post：429 归到 RATE_LIMITED")
    void postTooManyRequestsBecomesRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> new LlmHttpClient().post(url("/v1/chat"), Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.RATE_LIMITED);
    }

    @Test
    @DisplayName("post：500 归到 UPSTREAM_ERROR")
    void postServerErrorBecomesUpstreamError() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThatThrownBy(() -> new LlmHttpClient().post(url("/v1/chat"), Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("post：读超时归到 TIMEOUT")
    void postTimeoutBecomesTimeout() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true}")
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertThatThrownBy(() -> SHORT_TIMEOUT_CLIENT.post(url("/v1/chat"), Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.TIMEOUT);
    }

    @Test
    @DisplayName("post：连不上归到 UPSTREAM_ERROR（不是超时）")
    void postConnectionRefusedBecomesUpstreamError() throws IOException {
        MockWebServer closed = new MockWebServer();
        closed.start();
        String deadUrl = closed.url("/v1/chat").toString();
        closed.shutdown();

        assertThatThrownBy(() -> new LlmHttpClient().post(deadUrl, Map.of(), "{}"))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.UPSTREAM_ERROR);
    }

    // ── 流式请求 ────────────────────────────────────────────────────

    @Test
    @DisplayName("stream：逐条回调 SSE 事件，心跳注释被忽略")
    void streamDeliversEvents() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(": ping\n\n"
                        + "data: {\"delta\":\"hi\"}\n\n"
                        + "data: [DONE]\n\n"
                        + "event: content_block_delta\ndata: {\"delta\":\"yo\"}\n\n"));

        List<String> types = new ArrayList<>();
        List<String> datas = new ArrayList<>();
        new LlmHttpClient().stream(url("/v1/chat/stream"),
                Map.of("Accept", "text/event-stream"),
                "{\"stream\":true}",
                (type, data) -> {
                    types.add(type);
                    datas.add(data);
                });

        // 心跳注释行不产生事件；没写 event: 的帧 type 为 null
        assertThat(types).containsExactly(null, null, "content_block_delta");
        assertThat(datas).containsExactly("{\"delta\":\"hi\"}", "[DONE]", "{\"delta\":\"yo\"}");
    }

    @Test
    @DisplayName("stream：请求头与请求体照发")
    void streamSendsRequest() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: [DONE]\n\n"));

        new LlmHttpClient().stream(url("/v1/chat/stream"),
                Map.of("Accept", "text/event-stream", "Authorization", "Bearer sk-test"),
                "{\"model\":\"gpt-x\"}",
                (type, data) -> { });

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"model\":\"gpt-x\"}");
        assertThat(request.getHeader("Accept")).isEqualTo("text/event-stream");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer sk-test");
    }

    @Test
    @DisplayName("stream：CRLF 分隔与多行 data 都能正确分帧")
    void streamHandlesCrlfAndMultiLineData() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: delta\r\ndata: 第一行\r\ndata: 第二行\r\n\r\n"));

        List<String> types = new ArrayList<>();
        List<String> datas = new ArrayList<>();
        new LlmHttpClient().stream(url("/v1/chat/stream"), Map.of(), "{}",
                (type, data) -> {
                    types.add(type);
                    datas.add(data);
                });

        assertThat(types).containsExactly("delta");
        // 一条事件里的多行 data 按 \n 拼接
        assertThat(datas).containsExactly("第一行\n第二行");
    }

    @Test
    @DisplayName("stream：事件类型只在带 event: 的那一帧上生效")
    void streamResetsEventTypeBetweenEvents() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: delta\ndata: {\"a\":1}\n\ndata: {\"b\":2}\n\n"));

        List<String> types = new ArrayList<>();
        new LlmHttpClient().stream(url("/v1/chat/stream"), Map.of(), "{}",
                (type, data) -> types.add(type));

        assertThat(types).containsExactly("delta", null);
    }

    @Test
    @DisplayName("stream：最后一条事件没有以空行收尾时丢弃")
    void streamDiscardsTrailingEventWithoutBlankLine() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"a\":1}\n\ndata: {\"b\":2}\n"));

        List<String> datas = new ArrayList<>();
        new LlmHttpClient().stream(url("/v1/chat/stream"), Map.of(), "{}",
                (type, data) -> datas.add(data));

        assertThat(datas).containsExactly("{\"a\":1}");
    }

    @Test
    @DisplayName("stream：401 归到 AUTH_FAILED")
    void streamUnauthorizedBecomesAuthFailed() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"bad key\"}"));

        assertThatThrownBy(() -> new LlmHttpClient()
                .stream(url("/v1/chat/stream"), Map.of(), "{}", (type, data) -> { }))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.AUTH_FAILED);
    }

    @Test
    @DisplayName("stream：429 归到 RATE_LIMITED")
    void streamTooManyRequestsBecomesRateLimited() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> new LlmHttpClient()
                .stream(url("/v1/chat/stream"), Map.of(), "{}", (type, data) -> { }))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.RATE_LIMITED);
    }

    @Test
    @DisplayName("stream：中途读超时归到 TIMEOUT")
    void streamTimeoutBecomesTimeout() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"delta\":\"hi\"}\n\n")
                .setBodyDelay(2, TimeUnit.SECONDS));

        assertThatThrownBy(() -> SHORT_TIMEOUT_CLIENT
                .stream(url("/v1/chat/stream"), Map.of(), "{}", (type, data) -> { }))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.TIMEOUT);
    }

    @Test
    @DisplayName("stream：响应不是 SSE（Content-Type 不对）归到 UPSTREAM_ERROR")
    void streamNonSseResponseBecomesUpstreamError() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("not an event stream"));

        assertThatThrownBy(() -> new LlmHttpClient()
                .stream(url("/v1/chat/stream"), Map.of(), "{}", (type, data) -> { }))
                .isInstanceOf(LlmApiException.class)
                .extracting(e -> ((LlmApiException) e).getKind())
                .isEqualTo(LlmErrorKind.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("stream：回调抛出的异常原样上抛，不被当成上游故障")
    void streamPropagatesCallbackException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"delta\":\"hi\"}\n\n"));

        assertThatThrownBy(() -> new LlmHttpClient()
                .stream(url("/v1/chat/stream"), Map.of(), "{}", (type, data) -> {
                    throw new IllegalStateException("回调炸了");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("回调炸了");
    }

    // ── 异常与错误码的对应 ──────────────────────────────────────────

    @Test
    @DisplayName("失败分类映射到对应的错误码与 HTTP 状态")
    void kindsMapToErrorCodes() {
        assertThat(LlmErrorKind.TIMEOUT.getErrorCode().getHttpStatus().value()).isEqualTo(504);
        assertThat(LlmErrorKind.RATE_LIMITED.getErrorCode().getHttpStatus().value()).isEqualTo(429);
        // 上游拒了我们的 key，不是终端用户没登录——不能映射成 401
        assertThat(LlmErrorKind.AUTH_FAILED.getErrorCode().getHttpStatus().value()).isEqualTo(502);
        assertThat(LlmErrorKind.UPSTREAM_ERROR.getErrorCode().getHttpStatus().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("LlmApiException 带着失败分类，message 里没有上游响应体")
    void exceptionCarriesKindWithoutUpstreamBody() {
        LlmApiException exception = new LlmApiException(LlmErrorKind.RATE_LIMITED, "LLM 上游限流: HTTP 429");

        assertThat(exception.getKind()).isEqualTo(LlmErrorKind.RATE_LIMITED);
        assertThat(exception.getErrorCode()).isEqualTo(LlmErrorKind.RATE_LIMITED.getErrorCode());
        assertThat(exception.getMessage()).doesNotContain("sk-");
    }
}
