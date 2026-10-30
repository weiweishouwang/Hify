package com.hify.shared.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hify.shared.context.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestLogInterceptor} 的单元测试。
 */
class RequestLogInterceptorTest {

    private final RequestLogInterceptor interceptor = new RequestLogInterceptor();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogInterceptor.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        TraceContext.clear();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    @DisplayName("请求入口写 traceId 进 MDC，请求结束清理干净")
    void writesTraceIdOnEntryAndClearsOnExit() {
        MockHttpServletRequest request = request("GET", "/api/v1/demo-items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThat(TraceContext.get()).isNotBlank();
        assertThat(MDC.get(TraceContext.MDC_KEY)).isEqualTo(TraceContext.get());
        // 没有 OpenTelemetry 时是自己生成的 32 位十六进制
        assertThat(TraceContext.get()).matches("[0-9a-f]{32}");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(TraceContext.get()).isNull();
        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("正常请求记 INFO，带 method、path、status、耗时")
    void logsNormalRequestAtInfo() {
        MockHttpServletRequest request = request("GET", "/api/v1/demo-items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("method=GET")
                .contains("path=/api/v1/demo-items")
                .contains("status=200")
                .contains("cost=");
    }

    @Test
    @DisplayName("超过 1 秒的慢请求记 WARN")
    void logsSlowRequestAtWarn() {
        MockHttpServletRequest request = request("POST", "/api/v1/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.preHandle(request, response, new Object());
        // 假装已经过去 1.5 秒
        request.setAttribute(RequestLogInterceptor.START_TIME_ATTRIBUTE,
                System.nanoTime() - 1_500_000_000L);
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("慢请求").contains("method=POST");
    }

    @Test
    @DisplayName("请求出错时照样清理 MDC")
    void clearsMdcEvenWhenRequestFails() {
        MockHttpServletRequest request = request("GET", "/api/v1/demo-items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("炸了"));

        assertThat(TraceContext.get()).isNull();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("status=500");
    }
}
