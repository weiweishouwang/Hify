package com.hify.shared.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link TraceContext} 的单元测试。 */
class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    @DisplayName("设置后可取回，且同步写入 MDC")
    void 设置与取回() {
        TraceContext.set("abc123");

        assertThat(TraceContext.get()).isEqualTo("abc123");
        assertThat(MDC.get(TraceContext.MDC_KEY)).isEqualTo("abc123");
    }

    @Test
    @DisplayName("未设置时返回 null，MDC 中也没有")
    void 未设置() {
        assertThat(TraceContext.get()).isNull();
        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("clear 同时清理 ThreadLocal 与 MDC")
    void clear清空() {
        TraceContext.set("abc123");
        TraceContext.clear();

        assertThat(TraceContext.get()).isNull();
        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("传入空白值等同于清理")
    void 空白值等同清理() {
        TraceContext.set("abc123");
        TraceContext.set("  ");

        assertThat(TraceContext.get()).isNull();
        assertThat(MDC.get(TraceContext.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("生成的 traceId 是 32 位十六进制")
    void 生成格式() {
        String traceId = TraceContext.newTraceId();

        assertThat(traceId).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(TraceContext.newTraceId()).isNotEqualTo(traceId);
    }

    @Test
    @DisplayName("线程之间不共享")
    void 线程间不共享() throws Exception {
        TraceContext.set("main-trace");

        AtomicReference<String> inOtherThread = new AtomicReference<>();
        Thread thread = new Thread(() -> inOtherThread.set(TraceContext.get()));
        thread.start();
        thread.join();

        assertThat(inOtherThread.get()).isNull();
        assertThat(TraceContext.get()).isEqualTo("main-trace");
    }
}
