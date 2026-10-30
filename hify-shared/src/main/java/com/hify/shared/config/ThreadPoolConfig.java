package com.hify.shared.config;

import com.hify.shared.concurrent.NamedThreadFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 共享线程池。
 *
 * <p>两个池按用途分开，参数依据是「任务会不会长时间占住线程」：
 *
 * <ul>
 *   <li>{@link #llmExecutor} —— LLM / MCP 这类慢调用。IO 密集，单次占用时间长，
 *       队列不能太长（排队久了用户早就走了），线程数给到 50。</li>
 *   <li>{@link #asyncExecutor} —— 日志异步写入这类非关键任务。丢一两条不影响业务，
 *       队列可以长一些，线程数少一些，把机器资源留给 LLM。</li>
 * </ul>
 *
 * <p>按 CLAUDE.md §C2：队列必须有界（无界队列会 OOM），线程工厂必须带业务名，
 * 拒绝策略必须明确——两个池都用 {@code CallerRunsPolicy}，队列满了就让调用方线程自己跑，
 * 宁可慢一点也不丢任务。
 *
 * <p><b>注意</b>：注册了 {@code Executor} 类型的 bean 之后，Spring Boot 的
 * {@code TaskExecutionAutoConfiguration} 会因为 {@code @ConditionalOnMissingBean(Executor.class)}
 * 而不再创建 {@code applicationTaskExecutor}，也就是 {@code @Async} 的默认执行器没了。
 * 要用 {@code @Async} 时必须显式指定执行器（{@code @Async("llmExecutor")}），
 * 或者另外声明一个名为 {@code taskExecutor} 的 {@code TaskExecutor}。
 */
@Configuration(proxyBeanMethods = false)
public class ThreadPoolConfig {

    /** LLM 调用线程池的 bean 名，注入时用 {@code @Qualifier(ThreadPoolConfig.LLM_EXECUTOR)}。 */
    public static final String LLM_EXECUTOR = "llmExecutor";

    /** 通用异步任务线程池的 bean 名。 */
    public static final String ASYNC_EXECUTOR = "asyncExecutor";

    // ── llmExecutor：LLM / MCP 等慢调用 ────────────────────────────
    /** 核心线程数。IO 密集，核心线程先占满等着接活。 */
    private static final int LLM_CORE_POOL_SIZE = 10;

    /** 最大线程数。队列满了之后继续加线程，最多加到这里。 */
    private static final int LLM_MAX_POOL_SIZE = 50;

    /** 等待队列容量。有界，满了走拒绝策略。 */
    private static final int LLM_QUEUE_CAPACITY = 100;

    // ── asyncExecutor：日志异步写入等非关键任务 ────────────────────
    /** 核心线程数。 */
    private static final int ASYNC_CORE_POOL_SIZE = 5;

    /** 最大线程数。 */
    private static final int ASYNC_MAX_POOL_SIZE = 20;

    /** 等待队列容量。非关键任务可以多排一会儿。 */
    private static final int ASYNC_QUEUE_CAPACITY = 200;

    /** 核心线程空闲多久后回收（超出核心线程数的那些）。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    /**
     * LLM 调用线程池。
     *
     * @return 线程池实例；关闭时由 Spring 调用 {@code shutdown()} 优雅收尾
     */
    @Bean(name = LLM_EXECUTOR)
    @Qualifier(LLM_EXECUTOR)
    public ThreadPoolExecutor llmExecutor() {
        return new ThreadPoolExecutor(
                LLM_CORE_POOL_SIZE,
                LLM_MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(LLM_QUEUE_CAPACITY),
                new NamedThreadFactory("llm-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 通用异步任务线程池。
     *
     * @return 线程池实例；关闭时由 Spring 调用 {@code shutdown()} 优雅收尾
     */
    @Bean(name = ASYNC_EXECUTOR)
    @Qualifier(ASYNC_EXECUTOR)
    public ThreadPoolExecutor asyncExecutor() {
        return new ThreadPoolExecutor(
                ASYNC_CORE_POOL_SIZE,
                ASYNC_MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
                new NamedThreadFactory("async-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
