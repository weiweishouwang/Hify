package com.hify.conversation.internal.controller;

import com.hify.conversation.api.SendMessageReq;
import com.hify.conversation.internal.entity.ConversationDO;
import com.hify.conversation.internal.service.ChatOrchestrator;
import com.hify.conversation.internal.service.ConversationLookup;
import com.hify.conversation.internal.service.ConversationSupport;
import com.hify.conversation.internal.service.StreamEventSink;
import com.hify.shared.api.CurrentUserService;
import com.hify.shared.config.ThreadPoolConfig;
import com.hify.shared.context.TraceContext;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式对话的调度：把「编排」与「写出」拆到两条线程上。
 *
 * <p><b>两条线程</b>（CLAUDE.md §3.4）：
 * <ul>
 *   <li>{@code llmExecutor} —— 调上游模型、执行工具、写库</li>
 *   <li>{@code sseWriterExecutor} —— 只负责把事件写进 {@link SseEmitter}</li>
 * </ul>
 * 两者之间用一个<b>有界队列</b>交接。
 *
 * <p><b>队列满了怎么办 —— 背压</b>：编排线程阻塞等写出腾出空间。
 * 丢弃事件会丢掉模型输出；无界缓冲会把内存打满。背压是唯一两头都不吃亏的选项。
 */
@Component
public class ConversationStreamDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ConversationStreamDispatcher.class);

    /** 事件队列容量。 */
    private static final int QUEUE_CAPACITY = 256;

    /** 队列等待的最长时间；到了就再看一眼流是不是已经断了。 */
    private static final long OFFER_TIMEOUT_MS = 200L;

    private final ChatOrchestrator orchestrator;
    private final ConversationLookup conversations;
    private final CurrentUserService currentUsers;
    private final ThreadPoolExecutor llmExecutor;
    private final ThreadPoolExecutor sseWriter;

    public ConversationStreamDispatcher(ChatOrchestrator orchestrator, ConversationLookup conversations,
                                        CurrentUserService currentUsers,
                                        @Qualifier(ThreadPoolConfig.LLM_EXECUTOR) ThreadPoolExecutor llmExecutor,
                                        @Qualifier("sseWriterExecutor") ThreadPoolExecutor sseWriter) {
        this.orchestrator = orchestrator;
        this.conversations = conversations;
        this.currentUsers = currentUsers;
        this.llmExecutor = llmExecutor;
        this.sseWriter = sseWriter;
    }

    /**
     * 起一条流式对话。
     *
     * @param sessionPublicId 会话的对外 ID
     * @param request         发送消息的请求
     * @param emitter         SSE 写出器
     */
    public void stream(String sessionPublicId, SendMessageReq request, SseEmitter emitter) {
        // 先校验会话归属：会话不存在或不是当前用户的，要用正常的 404 返回。
        // 等流开始了再报错就晚了 —— 那时 HTTP 状态已经发出去了，客户端只能收到一帧 error。
        ConversationDO session = conversations.requireOwned(sessionPublicId);
        String content = ConversationSupport.content(request.getContent());

        BlockingQueue<StreamEvent> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicBoolean finished = new AtomicBoolean(false);
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onError(throwable -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));

        try {
            sseWriter.execute(() -> writeLoop(queue, emitter, disconnected));
        } catch (RejectedExecutionException exception) {
            log.warn("SSE 并发连接过多，写出任务被拒绝");
            emitter.completeWithError(new BizException(ErrorCode.TOO_MANY_REQUESTS, "并发连接过多，请稍后重试"));
            return;
        }

        // 用户 id 在 HTTP 线程上取 —— 编排跑在线程池里，ThreadLocal 那边是空的
        Long userId = currentUsers.currentUserId();
        StreamEventSink sink = new QueueSink(queue, disconnected);
        try {
            llmExecutor.execute(() -> orchestrate(session, content, userId, sink, queue, finished));
        } catch (RejectedExecutionException exception) {
            log.warn("模型调用线程池繁忙，本轮对话被拒绝");
            sink.emit("error", payload("code", 42900, "message", "模型调用繁忙，请稍后重试"));
            signalEnd(queue, finished);
        }
    }

    /** 编排线程：跑对话，结束时给写出线程一个结束信号。 */
    private void orchestrate(ConversationDO session, String content, Long userId, StreamEventSink sink,
                              BlockingQueue<StreamEvent> queue, AtomicBoolean finished) {
        String traceId = TraceContext.get();
        try {
            MDC.put(TraceContext.MDC_KEY, traceId);
            orchestrator.run(session, content, userId, sink);
        } catch (RuntimeException exception) {
            log.warn("流式对话异常 session={} reason={}", session.getPublicId(),
                    exception.getClass().getSimpleName());
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
            signalEnd(queue, finished);
        }
    }

    /** 给写出线程一个结束信号（只发一次）。 */
    private void signalEnd(BlockingQueue<StreamEvent> queue, AtomicBoolean finished) {
        if (finished.getAndSet(true)) {
            return;
        }
        try {
            queue.offer(StreamEvent.EOF, 200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** 写出线程：把队列里的事件逐条写进 SSE 响应。 */
    private void writeLoop(BlockingQueue<StreamEvent> queue, SseEmitter emitter, AtomicBoolean disconnected) {
        try {
            for (;;) {
                StreamEvent event = queue.take();
                if (event == StreamEvent.EOF) {
                    break;
                }
                emitter.send(SseEmitter.event().name(event.name()).data(event.payload()));
            }
            emitter.complete();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalStateException exception) {
            // 客户端断开：把流标记成关闭，编排线程会尽快收手
            disconnected.set(true);
            log.info("SSE 客户端断开，结束写出");
        }
    }

    /** 事件出口：把事件塞进队列。队列满时阻塞等待（背压）。 */
    private static final class QueueSink implements StreamEventSink {

        private final BlockingQueue<StreamEvent> queue;
        private final AtomicBoolean disconnected;

        QueueSink(BlockingQueue<StreamEvent> queue, AtomicBoolean disconnected) {
            this.queue = queue;
            this.disconnected = disconnected;
        }

        @Override
        public void emit(String event, Object payload) {
            if (disconnected.get()) {
                return;
            }
            try {
                while (!disconnected.get()) {
                    if (queue.offer(new StreamEvent(event, payload), OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public boolean isClosed() {
            return disconnected.get();
        }
    }

    /**
     * 一次流事件。
     *
     * @param name    事件名；{@code null} 表示结束哨兵
     * @param payload 事件数据
     */
    private record StreamEvent(String name, Object payload) {

        /** 结束哨兵。 */
        static final StreamEvent EOF = new StreamEvent(null, null);
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }
}
