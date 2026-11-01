package com.hify.conversation.internal.config;

import com.hify.shared.concurrent.NamedThreadFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 写出线程池。
 *
 * <p><b>为什么要单独一个池</b>：CLAUDE.md §3.4 写死了「往浏览器写帧的线程必须和
 * llmExecutor 分开」—— 一个慢客户端的阻塞写如果占住 llmExecutor 线程，
 * 就等于吃掉一个 LLM 并发名额。上游读在 llmExecutor 上，写在本池上，两者不互相拖累。
 *
 * <p><b>参数</b>：核心 48 / 最大 200 / {@link SynchronousQueue}。
 * 队列长度为 0 意味着「超过核心线程数就立刻新建线程」，与 CLAUDE.md 给的
 * {@code boundedElastic(48, 1000)} 是同一个弹性语义：并发流多了就多开线程，
 * 而不是排在一个长队里等。
 *
 * <p><b>拒绝策略是 AbortPolicy</b>：超过 200 条并发流时<b>快速失败</b>
 * （明确报错「并发连接过多」）。不能用 CallerRuns —— 那会让写出回落到编排线程上，
 * 等于绕回我们要避免的耦合。
 */
@Configuration(proxyBeanMethods = false)
public class SseWriterConfig {

    /** SSE 写出线程池的 bean 名。 */
    public static final String SSE_WRITER_EXECUTOR = "sseWriterExecutor";

    /** 核心线程数。 */
    private static final int CORE_POOL_SIZE = 48;

    /** 最大线程数。 */
    private static final int MAX_POOL_SIZE = 200;

    /** 空闲回收时间。 */
    private static final long KEEP_ALIVE_SECONDS = 60L;

    @Bean(name = SSE_WRITER_EXECUTOR)
    @Qualifier(SSE_WRITER_EXECUTOR)
    public ThreadPoolExecutor sseWriterExecutor() {
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new NamedThreadFactory("sse-write-"),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
