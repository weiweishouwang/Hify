package com.hify.shared.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生产环境的 JSON 编码器（Spring Boot 自带的 {@link StructuredLogEncoder}）。
 */
class StructuredLogEncoderTest {

    private static LoggingEvent event(Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setLoggerName("com.hify.shared.log.RequestLogInterceptor");
        event.setThreadName("http-nio-8080-exec-1");
        event.setMessage("请求完成 method=GET path=/api/v1/demo-items status=200 cost=3ms");
        event.setTimeStamp(1760000000000L);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    @Test
    @DisplayName("logstash 格式：一行一条 JSON，并带上 MDC 里的 traceId")
    void logstashFormatProducesJsonWithMdcTraceId() throws Exception {
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat("logstash");
        // 编码器要从 Logback 上下文里拿 Environment（Boot 启动时装进去的）
        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        encoder.setContext(context);
        encoder.start();

        String json = new String(encoder.encode(event(Map.of("traceId", "0123456789abcdef0123456789abcdef"))),
                StandardCharsets.UTF_8);
        System.out.println("JSON = " + json);

        JsonNode node = new ObjectMapper().readTree(json);
        assertThat(node.get("message").asText()).contains("请求完成");
        assertThat(node.get("logger_name").asText()).isEqualTo("com.hify.shared.log.RequestLogInterceptor");
        assertThat(node.get("level").asText()).isEqualTo("INFO");
        assertThat(node.get("traceId").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
        // 一行一条
        assertThat(json.strip()).doesNotContain("\n");
    }
}
