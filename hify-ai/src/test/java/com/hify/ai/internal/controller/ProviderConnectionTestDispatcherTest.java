package com.hify.ai.internal.controller;

import com.hify.ai.api.ProviderService;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderConnectionTestDispatcherTest {
    @Test
    void saturatedCallerRunsPoolRejectsWithoutRunningProviderTestInCaller() throws Exception {
        var service = mock(ProviderService.class);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new com.hify.shared.concurrent.NamedThreadFactory("test-llm"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            pool.execute(() -> {
                started.countDown();
                try { release.await(); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            pool.execute(() -> { });
            var result = new ProviderConnectionTestDispatcher(service, pool).testConnection("provider");
            assertThat(result.getResult()).isInstanceOfSatisfying(BizException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
            verifyNoInteractions(service);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void stoppedPoolDoesNotLeaveUncompletedHttpRequest() {
        var service = mock(ProviderService.class);
        var pool = new com.hify.shared.config.ThreadPoolConfig().llmExecutor();
        pool.shutdown();
        var result = new ProviderConnectionTestDispatcher(service, pool).testConnection("provider");
        assertThat(result.hasResult()).isTrue();
        assertThat(result.getResult()).isInstanceOf(BizException.class);
        verifyNoInteractions(service);
    }
}
