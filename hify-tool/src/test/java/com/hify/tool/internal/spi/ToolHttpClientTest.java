package com.hify.tool.internal.spi;

import com.hify.shared.json.JsonUtils;
import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 自定义 HTTP 工具的上游调用：参数映射、错误分类与响应截断。 */
class ToolHttpClientTest {

    MockWebServer server;
    ToolHttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new ToolHttpClient(new JsonUtils(new ObjectMapper()));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    String url(String path) {
        return server.url(path).toString();
    }

    @Test
    @DisplayName("POST 把入参整体作为 JSON 请求体")
    void postSendsJsonBody() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}"));

        var response = client.call("POST", url("/weather"), Map.of("Content-Type", "application/json"),
                Map.of("city", "杭州"), 5000);

        assertThat(response.content()).isEqualTo("{\"ok\":true}");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"city\":\"杭州\"}");
    }

    @Test
    @DisplayName("GET 把入参摊平成查询串")
    void getFlattensQuery() throws Exception {
        server.enqueue(new MockResponse().setBody("sunny"));

        client.call("GET", url("/weather"), Map.of(), Map.of("city", "杭州", "days", 3), 5000);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).contains("city=").contains("days=3");
        assertThat(request.getPath()).doesNotContain("+");
    }

    @Test
    @DisplayName("上游返回错误状态时异常里只有状态码，不带响应体")
    void upstreamErrorDoesNotLeakBody() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("内部堆栈与用户输入"));

        assertThatThrownBy(() -> client.call("POST", url("/x"), Map.of(), Map.of(), 5000))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("500")
                .hasMessageNotContaining("内部堆栈");
    }

    @Test
    @DisplayName("401 与 403 归为鉴权失败")
    void authFailureIsClassified() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> client.call("POST", url("/x"), Map.of(), Map.of(), 5000))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).kind())
                .isEqualTo(ToolErrorKind.AUTH_FAILED);
    }

    @Test
    @DisplayName("超长响应被截断并标记")
    void longResponseIsTruncated() {
        server.enqueue(new MockResponse().setBody("x".repeat(ToolHttpClient.MAX_CONTENT_LENGTH + 100)));

        var response = client.call("POST", url("/x"), Map.of(), Map.of(), 5000);

        assertThat(response.truncated()).isTrue();
        assertThat(response.content()).hasSize(ToolHttpClient.MAX_CONTENT_LENGTH);
    }
}
