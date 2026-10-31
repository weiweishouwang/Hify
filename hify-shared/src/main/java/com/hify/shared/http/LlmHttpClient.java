package com.hify.shared.http;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调上游 LLM 服务的 HTTP 客户端。
 *
 * <p>两条路径：
 * <ul>
 *   <li>{@link #post} / {@link #get} —— 普通请求，用 {@link RestTemplate}，读超时 60 秒</li>
 *   <li>{@link #stream} —— 流式 SSE 请求，用 OkHttp 读响应体、自己解析 SSE 帧，读超时 120 秒</li>
 * </ul>
 *
 * <p>两条路径的失败都统一转成 {@link LlmApiException}，用 {@link LlmErrorKind}
 * 区分超时 / 鉴权失败 / 限流 / 其他上游故障。调用方不用去分辨
 * {@code HttpStatusCodeException}、{@code SocketTimeoutException} 这些底层类型。
 *
 * <p><b>每次请求都记一条日志</b>：URL、耗时、状态码。请求头与请求体不记——
 * 请求头里是 API Key，请求体里是用户内容（CLAUDE.md §3.1 L4）。
 *
 * <p><b>线程模型</b>：两个方法都是阻塞的。{@link #post} 会占住调用线程直到上游返回；
 * {@link #stream} 会占住调用线程直到整条流结束。调用方要放在专用线程上跑，
 * 不要占用 Tomcat 工作线程（CLAUDE.md §3.4）。
 *
 * <p>两个客户端的超时不同是有意为之：普通请求 60 秒读超时足够覆盖一次非流式生成；
 * 流式请求的整体时长可能更长（模型慢、上下文长），读超时给到 120 秒。
 */
public class LlmHttpClient {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpClient.class);

    /** 默认 Content-Type。LLM provider 的请求体一律是 JSON。 */
    private static final String DEFAULT_CONTENT_TYPE = "application/json; charset=utf-8";

    /** 连接超时：5 秒。 */
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 普通请求的读超时：60 秒。 */
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    /** 流式请求的读超时：120 秒。 */
    public static final Duration DEFAULT_STREAM_READ_TIMEOUT = Duration.ofSeconds(120);

    /** 上游响应体写进日志时的最大长度，防止一条巨型报错刷爆日志。 */
    private static final int MAX_LOGGED_BODY = 512;

    private final RestTemplate restTemplate;
    private final OkHttpClient streamingClient;

    /** 用默认超时（连接 5s / 普通读 60s / 流式读 120s）。 */
    public LlmHttpClient() {
        this(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, DEFAULT_STREAM_READ_TIMEOUT);
    }

    /**
     * 指定三档超时。测试可以把超时调得很短，不必真的等一分钟。
     *
     * @param connectTimeout     连接超时
     * @param readTimeout        普通请求的读超时
     * @param streamReadTimeout  流式请求的读超时
     */
    public LlmHttpClient(Duration connectTimeout, Duration readTimeout, Duration streamReadTimeout) {
        this.restTemplate = buildRestTemplate(connectTimeout, readTimeout);
        this.streamingClient = buildStreamingClient(connectTimeout, streamReadTimeout);
    }

    /**
     * 发一次普通（非流式）POST 请求。
     *
     * @param url     完整请求地址
     * @param headers 请求头；{@code Content-Type} 缺省为 {@code application/json; charset=utf-8}
     * @param body    请求体
     * @return 上游返回的响应体
     * @throws LlmApiException 上游返回错误状态、超时或连接失败
     */
    public String post(String url, Map<String, String> headers, String body) {
        return exchange(url, headers, body, HttpMethod.POST);
    }

    /** 只读端点探测，复用普通请求的超时、日志和错误分类，不发送请求体。 */
    public String get(String url, Map<String, String> headers) {
        return exchange(url, headers, null, HttpMethod.GET);
    }

    private String exchange(String url, Map<String, String> headers, String body, HttpMethod method) {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        if (HttpMethod.POST.equals(method) && !httpHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            httpHeaders.set(HttpHeaders.CONTENT_TYPE, DEFAULT_CONTENT_TYPE);
        }
        HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);

        long startNanos = System.nanoTime();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            String content = response.getBody();
            log.info("LLM 请求完成 url={} status={} cost={}ms length={}",
                    url, response.getStatusCode().value(), elapsedMillis(startNanos), lengthOf(content));
            return content;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("LLM 请求被上游拒绝 url={} status={} cost={}ms",
                    url, status, elapsedMillis(startNanos));
            log.debug("LLM 上游错误响应体 url={} body={}", url, truncate(e.getResponseBodyAsString()));
            throw new LlmApiException(kindOf(status), "LLM 上游返回错误状态: " + status, e);
        } catch (ResourceAccessException e) {
            log.warn("LLM 请求失败 url={} cost={}ms reason={}",
                    url, elapsedMillis(startNanos), e.getClass().getSimpleName());
            throw translateIoFailure(e, url);
        } catch (RestClientException e) {
            log.warn("LLM 请求异常 url={} cost={}ms reason={}",
                    url, elapsedMillis(startNanos), e.getClass().getSimpleName());
            throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "LLM 请求失败: " + url, e);
        }
    }

    /**
     * 发一次流式 SSE 请求，逐条把事件回调出去。
     *
     * <p><b>阻塞到整条流结束才返回</b>：期间上游每发一条事件就调一次
     * {@link LlmStreamCallback#onEvent}。流正常结束返回；上游报错或超时抛
     * {@link LlmApiException}。回调跑在调用方的线程上。
     *
     * <p>请求必须是 POST 且带 body（LLM provider 的流式接口都是这个形态）。
     * 上游必须返回 {@code Content-Type: text/event-stream}，否则按上游故障处理。
     *
     * @param url      完整请求地址
     * @param headers  请求头；建议带 {@code Accept: text/event-stream}
     * @param body     请求体
     * @param callback 事件回调
     * @throws LlmApiException 上游返回错误状态、超时、连接失败，或响应不是 SSE
     */
    public void stream(String url, Map<String, String> headers, String body, LlmStreamCallback callback) {
        Request request = buildRequest(url, headers, body);

        long startNanos = System.nanoTime();
        AtomicInteger eventCount = new AtomicInteger();
        int status = 0;
        try (Response response = streamingClient.newCall(request).execute()) {
            status = response.code();
            if (!response.isSuccessful()) {
                log.warn("LLM 流式请求被上游拒绝 url={} status={} cost={}ms",
                        url, status, elapsedMillis(startNanos));
                throw new LlmApiException(kindOf(status), "LLM 上游返回错误状态: " + status);
            }
            if (!isEventStream(response)) {
                log.warn("LLM 流式响应格式不对 url={} status={} contentType={}",
                        url, status, response.header(HttpHeaders.CONTENT_TYPE));
                throw new LlmApiException(LlmErrorKind.UPSTREAM_ERROR,
                        "LLM 上游响应不是 SSE: " + response.header(HttpHeaders.CONTENT_TYPE));
            }
            SseFrameReader.readAll(response, (type, data) -> {
                eventCount.incrementAndGet();
                callback.onEvent(type, data);
            });
        } catch (LlmApiException e) {
            // 上面已经记过日志，原样上抛
            throw e;
        } catch (IOException e) {
            log.warn("LLM 流式请求失败 url={} cost={}ms reason={}",
                    url, elapsedMillis(startNanos), e.getClass().getSimpleName());
            throw translateIoFailure(e, url);
        } catch (RuntimeException e) {
            // 回调里抛的异常是调用方自己的问题，不是上游故障，原样上抛
            log.warn("LLM 流式回调异常 url={} cost={}ms", url, elapsedMillis(startNanos), e);
            throw e;
        }
        log.info("LLM 流式请求完成 url={} status={} cost={}ms events={}",
                url, status, elapsedMillis(startNanos), eventCount.get());
    }

    // ── 客户端构造 ──────────────────────────────────────────────────

    /**
     * 构造带超时的 RestTemplate。
     *
     * <p><b>显式指定请求工厂</b>，不让 Spring 按 classpath 自己挑：挑出来的那个
     * 会随依赖树变化，而且 {@code OkHttp3ClientHttpRequestFactory} 在 Spring 6.2
     * 已经废弃。固定用 {@link SimpleClientHttpRequestFactory}，超时语义一目了然
     * （连接超时 + 读超时都是 socket 级的），也不引入额外依赖。
     */
    private static RestTemplate buildRestTemplate(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        RestTemplate template = new RestTemplate(factory);
        // 请求体与响应体一律 UTF-8，不依赖转换器的默认值
        template.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .forEach(converter -> ((StringHttpMessageConverter) converter)
                        .setDefaultCharset(StandardCharsets.UTF_8));
        return template;
    }

    /** 构造发流式请求用的 OkHttp 客户端。 */
    private static OkHttpClient buildStreamingClient(Duration connectTimeout, Duration readTimeout) {
        return new OkHttpClient.Builder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }

    // ── 请求构造 ────────────────────────────────────────────────────

    /** 构造 OkHttp 请求。Content-Type 走 RequestBody 的 mediaType，不再单独加一遍头。 */
    private static Request buildRequest(String url, Map<String, String> headers, String body) {
        Map<String, String> safeHeaders = headers == null ? Map.of() : headers;
        String contentType = findHeader(safeHeaders, HttpHeaders.CONTENT_TYPE);
        MediaType mediaType = MediaType.parse(contentType != null ? contentType : DEFAULT_CONTENT_TYPE);
        if (mediaType == null) {
            mediaType = MediaType.parse(DEFAULT_CONTENT_TYPE);
        }
        RequestBody requestBody = RequestBody.create(body == null ? "" : body, mediaType);

        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        safeHeaders.forEach((name, value) -> {
            if (!HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                builder.addHeader(name, value);
            }
        });
        return builder.build();
    }

    /** 按名字找请求头，不区分大小写。 */
    private static String findHeader(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ── 失败分类 ────────────────────────────────────────────────────

    /** 按 HTTP 状态码分类。 */
    private static LlmErrorKind kindOf(int status) {
        if (status == 401 || status == 403) {
            return LlmErrorKind.AUTH_FAILED;
        }
        if (status == 429) {
            return LlmErrorKind.RATE_LIMITED;
        }
        return LlmErrorKind.UPSTREAM_ERROR;
    }

    /** 判断响应是不是 SSE：{@code Content-Type} 必须是 {@code text/event-stream}。 */
    private static boolean isEventStream(Response response) {
        String raw = response.header(HttpHeaders.CONTENT_TYPE);
        if (raw == null) {
            return false;
        }
        MediaType contentType = MediaType.parse(raw);
        return contentType != null
                && "text".equals(contentType.type())
                && "event-stream".equals(contentType.subtype());
    }

    /** 网络层失败：超时归 TIMEOUT，其余归 UPSTREAM_ERROR。 */
    private static LlmApiException translateIoFailure(Throwable t, String url) {
        if (isTimeout(t)) {
            return new LlmApiException(LlmErrorKind.TIMEOUT, "LLM 请求超时: " + url, t);
        }
        return new LlmApiException(LlmErrorKind.UPSTREAM_ERROR, "LLM 请求失败: " + url, t);
    }

    /** 沿 cause 链找超时。{@code SocketTimeoutException} 是 {@code InterruptedIOException} 的子类。 */
    private static boolean isTimeout(Throwable t) {
        for (Throwable current = t; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException || current instanceof HttpTimeoutException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    // ── 小工具 ──────────────────────────────────────────────────────

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_LOGGED_BODY ? text : text.substring(0, MAX_LOGGED_BODY) + "...(已截断)";
    }
}
