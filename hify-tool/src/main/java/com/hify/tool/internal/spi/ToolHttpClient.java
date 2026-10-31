package com.hify.tool.internal.spi;

import com.hify.shared.json.JsonUtils;
import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 自定义 HTTP 工具的上游调用。
 *
 * <p><b>阻塞调用</b> —— 调用方必须把它提交到 {@code llmExecutor}，不要占住 Tomcat
 * 工作线程（CLAUDE.md §3.5）。
 *
 * <p>用 JDK 自带的 {@link HttpClient} 而不是 {@code LlmHttpClient}：后者是给模型调用用的，
 * 读超时 60 秒、日志前缀写死「LLM 请求」，语义都不对。
 *
 * <p><b>上游响应体不进异常 message</b> —— 上游报错文本可能夹带用户输入，
 * 随异常一路回显给终端用户那里。只在服务端记 DEBUG 日志，异常里只留状态码。
 */
@Component
public class ToolHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ToolHttpClient.class);

    /** 响应体写进结果时的最大长度，防止一条巨型响应撑爆上下文。 */
    public static final int MAX_CONTENT_LENGTH = 8 * 1024;

    /** 连接超时：5 秒。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client;
    private final JsonUtils json;

    public ToolHttpClient(JsonUtils json) {
        this.json = json;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 发起一次调用。
     *
     * @param method    GET 或 POST
     * @param url       请求地址
     * @param headers   请求头；敏感值已经解密
     * @param args      模型给出的入参
     * @param timeoutMs 超时毫秒数
     * @return 响应体（超长时截断）
     * @throws ToolExecutionException 上游报错、超时或连接失败时抛出
     */
    public ToolHttpResponse call(String method, String url, Map<String, String> headers,
                                 Map<String, Object> args, int timeoutMs) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        HttpRequest request = buildRequest(method, url, headers, safeArgs, timeoutMs);
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("工具上游返回错误状态 url={} status={} cost={}ms",
                        url, status, elapsedMillis(startNanos));
                log.debug("工具上游错误响应体 url={} body={}", url, truncate(response.body()));
                throw new ToolExecutionException(kindOf(status), "工具上游返回错误状态: " + status);
            }
            log.info("工具请求完成 url={} status={} cost={}ms length={}",
                    url, status, elapsedMillis(startNanos), lengthOf(response.body()));
            return ToolHttpResponse.of(response.body());
        } catch (IOException exception) {
            log.warn("工具请求失败 url={} cost={}ms reason={}",
                    url, elapsedMillis(startNanos), exception.getClass().getSimpleName());
            if (isTimeout(exception)) {
                throw new ToolExecutionException(ToolErrorKind.TIMEOUT, "工具请求超时: " + url, exception);
            }
            throw new ToolExecutionException(ToolErrorKind.UPSTREAM_ERROR, "工具请求失败: " + url, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException(ToolErrorKind.UPSTREAM_ERROR, "工具请求被中断: " + url, exception);
        }
    }

    private HttpRequest buildRequest(String method, String url, Map<String, String> headers,
                                     Map<String, Object> args, int timeoutMs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));
        try {
            headers.forEach(builder::header);
        } catch (IllegalArgumentException exception) {
            // JDK HttpClient 禁止设置 Host / Connection 这类受限头
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR,
                    "请求头不被支持: " + exception.getMessage(), exception);
        }
        if ("GET".equalsIgnoreCase(method)) {
            String query = buildQuery(args);
            if (!query.isEmpty()) {
                builder.uri(URI.create(url + "?" + query));
            }
            builder.GET();
        } else {
            if (!containsHeader(headers, "Content-Type")) {
                builder.header("Content-Type", "application/json; charset=utf-8");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(json.toJson(args), StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    /**
     * 把入参摊平成查询串。
     *
     * <p>对象与数组用 JSON 字符串表示 —— 上游多半只认标量，
     * 结构化参数请走 POST 的请求体。
     */
    private String buildQuery(Map<String, Object> args) {
        StringBuilder query = new StringBuilder();
        args.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                return;
            }
            String text = value instanceof String || value instanceof Number || value instanceof Boolean
                    ? String.valueOf(value)
                    : json.toJson(value);
            query.append(query.isEmpty() ? "" : "&")
                    .append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(text, StandardCharsets.UTF_8));
        });
        return query.toString();
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private static ToolErrorKind kindOf(int status) {
        if (status == 401 || status == 403) {
            return ToolErrorKind.AUTH_FAILED;
        }
        return ToolErrorKind.UPSTREAM_ERROR;
    }

    /** 沿 cause 链找超时。 */
    private static boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.io.InterruptedIOException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

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
        return text.length() <= 512 ? text : text.substring(0, 512) + "...(已截断)";
    }

    /**
     * 上游响应。
     *
     * @param content   响应体，超长时已截断
     * @param truncated 是否发生了截断
     */
    public record ToolHttpResponse(String content, boolean truncated) {

        static ToolHttpResponse of(String body) {
            if (body == null) {
                return new ToolHttpResponse("", false);
            }
            if (body.length() <= MAX_CONTENT_LENGTH) {
                return new ToolHttpResponse(body, false);
            }
            return new ToolHttpResponse(body.substring(0, MAX_CONTENT_LENGTH), true);
        }
    }
}
