package com.hify.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link ThreadPoolConfig} 的单元测试。 */
class ThreadPoolConfigTest {

    private final ThreadPoolConfig config = new ThreadPoolConfig();

    @Test
    @DisplayName("llmExecutor：核心 10、最大 50、队列 100、线程名 llm- 前缀、CallerRunsPolicy")
    void llmExecutorHasExpectedShape() {
        ThreadPoolExecutor pool = config.llmExecutor();
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(10);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(50);
            assertThat(pool.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(100);
            assertThat(pool.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("asyncExecutor：核心 5、最大 20、队列 200、线程名 async- 前缀、AbortPolicy")
    void asyncExecutorHasExpectedShape() {
        ThreadPoolExecutor pool = config.asyncExecutor();
        try {
            assertThat(pool.getCorePoolSize()).isEqualTo(5);
            assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
            assertThat(pool.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
            assertThat(pool.getQueue().remainingCapacity()).isEqualTo(200);
            assertThat(pool.getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("两个池的线程名带各自的前缀，且序号递增")
    void threadsAreNamedWithPrefix() throws Exception {
        ThreadPoolExecutor llm = config.llmExecutor();
        ThreadPoolExecutor async = config.asyncExecutor();
        try {
            AtomicReference<String> llmThread = new AtomicReference<>();
            AtomicReference<String> asyncThread = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(2);

            llm.execute(() -> {
                llmThread.set(Thread.currentThread().getName());
                done.countDown();
            });
            async.execute(() -> {
                asyncThread.set(Thread.currentThread().getName());
                done.countDown();
            });

            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(llmThread.get()).startsWith("llm-");
            assertThat(asyncThread.get()).startsWith("async-");
        } finally {
            llm.shutdownNow();
            async.shutdownNow();
        }
    }

    @Test
    @DisplayName("CallerRunsPolicy：被拒绝的任务在调用方线程执行，不丢任务也不抛异常")
    void rejectedTaskRunsInCallerThread() {
        ThreadPoolExecutor pool = config.llmExecutor();
        try {
            AtomicReference<String> ranOn = new AtomicReference<>();
            AtomicReference<Throwable> thrown = new AtomicReference<>();

            // 直接触发拒绝处理——这正是 ThreadPoolExecutor 队列满且线程数到顶时的动作
            try {
                pool.getRejectedExecutionHandler()
                        .rejectedExecution(() -> ranOn.set(Thread.currentThread().getName()), pool);
            } catch (Throwable t) {
                thrown.set(t);
            }

            assertThat(thrown.get()).isNull();
            assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("asyncExecutor 拒绝任务时抛异常，不在提交线程执行探测")
    void asyncRejectionNeverRunsInCallerThread() {
        ThreadPoolExecutor pool = config.asyncExecutor();
        try {
            AtomicReference<String> ranOn = new AtomicReference<>();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> pool.getRejectedExecutionHandler()
                    .rejectedExecution(() -> ranOn.set(Thread.currentThread().getName()), pool))
                    .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            assertThat(ranOn.get()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("bean 名与 @Qualifier 一致，其他模块按名字注入不会错")
    void beanNamesMatchQualifier() throws Exception {
        Method llm = ThreadPoolConfig.class.getMethod("llmExecutor");
        Method async = ThreadPoolConfig.class.getMethod("asyncExecutor");

        // 注意：@Bean.name() 在 Spring 6 返回 String[]，@Qualifier.value() 返回 String
        assertThat(llm.getAnnotation(Bean.class).name())
                .containsExactly(ThreadPoolConfig.LLM_EXECUTOR);
        assertThat(async.getAnnotation(Bean.class).name())
                .containsExactly(ThreadPoolConfig.ASYNC_EXECUTOR);
        assertThat(llm.getAnnotation(Qualifier.class).value())
                .isEqualTo(ThreadPoolConfig.LLM_EXECUTOR);
        assertThat(async.getAnnotation(Qualifier.class).value())
                .isEqualTo(ThreadPoolConfig.ASYNC_EXECUTOR);
    }
}
