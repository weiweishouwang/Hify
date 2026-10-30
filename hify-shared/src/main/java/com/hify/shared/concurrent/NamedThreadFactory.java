package com.hify.shared.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 带业务名前缀的线程工厂。
 *
 * <p>线程名是排查线上问题的主要线索：线程池里跑的什么任务，一眼能从
 * {@code jstack} 或日志里的线程名看出来。线程名格式为 {@code 前缀-序号}，
 * 如 {@code llm-1}、{@code llm-2}。
 *
 * <p>未捕获异常会被记录而不是静默消失——池化线程因为未捕获异常死掉时，
 * 任务连同异常一起没了，线上只会看到「任务没执行」却查不到原因。
 */
public class NamedThreadFactory implements ThreadFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NamedThreadFactory.class);

    private final String prefix;

    private final AtomicInteger counter = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
        thread.setDaemon(false);
        thread.setUncaughtExceptionHandler((t, e) ->
                LOG.error("线程 {} 出现未捕获异常", t.getName(), e));
        return thread;
    }

    /** 线程名前缀。 */
    public String getPrefix() {
        return prefix;
    }
}
