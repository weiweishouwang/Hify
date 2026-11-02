package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ChatAdapterFactory;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.redis.RateLimiter;
import com.hify.shared.resilience.CircuitBreakerService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式对话的实现。
 *
 * <p><b>纯能力层</b>：把消息发给模型，把增量吐回来。
 * 不检索、不拼上下文、不执行工具、不做工具循环 —— 那些是编排层的事
 * （CLAUDE.md §2.1 的模块不变量）。
 *
 * <p><b>调用是阻塞的</b>：要等上游把整条流吐完才返回。调用方要放在
 * {@code llmExecutor} 上跑，不要占 Tomcat 工作线程（CLAUDE.md §3.5）。
 *
 * <p><b>韧性在这一层接上</b>（CLAUDE.md §3.5）：
 * <ul>
 *   <li><b>每用户每 provider 在途上限</b> —— 一个人开多个标签页不能吃光整个连接池</li>
 *   <li><b>熔断在外、重试在内</b> —— 熔断按 provider 独立；一次逻辑调用内部重试，
 *       熔断器只记 1 次失败（失败率算的是逻辑调用，不是 HTTP 尝试）</li>
 *   <li><b>流式只在首个 chunk 之前重试</b> —— 已经吐过内容再重试，
 *       重复内容会被拼进用户的流</li>
 * </ul>
 */
@Service
public class ChatServiceImpl implements ChatService {

    /** 每个用户对同一个 provider 的并发在途上限。 */
    static final int MAX_INFLIGHT_PER_USER = 3;

    /** 在途计数的兜底过期时间：进程崩了没释放时，键到期自动消失。 */
    static final Duration INFLIGHT_TTL = Duration.ofSeconds(120);

    /** 在途计数的键前缀。 */
    static final String INFLIGHT_KEY_PREFIX = "hify:llm:inflight:";

    private final ModelMapper models;
    private final ModelProviderMapper providers;
    private final ChatAdapterFactory adapterFactory;
    private final CircuitBreakerService breakers;
    private final RateLimiter rateLimiter;

    public ChatServiceImpl(ModelMapper models, ModelProviderMapper providers, ChatAdapterFactory adapterFactory,
                           CircuitBreakerService breakers, RateLimiter rateLimiter) {
        this.models = models;
        this.providers = providers;
        this.adapterFactory = adapterFactory;
        this.breakers = breakers;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void stream(ChatRequest request, ChatListener listener) {
        if (request == null || request.getModelPublicId() == null || request.getModelPublicId().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型标识不能为空");
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "对话消息不能为空");
        }
        ModelDO model = models.selectOne(new QueryWrapper<ModelDO>()
                .eq("public_id", request.getModelPublicId().trim()));
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }
        if (!"chat".equals(model.getModelType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该模型不是对话模型");
        }
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw new BizException(ErrorCode.CONFLICT, "模型已停用");
        }
        ModelProviderDO provider = providers.selectById(model.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "接入点不存在");
        }
        if (!Boolean.TRUE.equals(provider.getEnabled())) {
            throw new BizException(ErrorCode.CONFLICT, "接入点已停用");
        }

        String inflightKey = inflightKey(request.getUserId(), provider.getPublicId());
        boolean limited = request.getUserId() != null;
        if (limited && !rateLimiter.tryAcquireInFlight(inflightKey, MAX_INFLIGHT_PER_USER, INFLIGHT_TTL)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "你当前有 " + MAX_INFLIGHT_PER_USER + " 个对话在进行中，请等待完成后再试");
        }
        try {
            streamWithResilience(provider, model, request, listener);
        } finally {
            if (limited) {
                rateLimiter.releaseInFlight(inflightKey);
            }
        }
    }

    /**
     * 熔断 + 重试地跑一次流式调用。
     *
     * <p><b>熔断器的粒度是接入点实例</b>，不是协议档位 —— 两个不同的 OpenAI 兼容
     * 端点应该各自独立：一个挂了不影响另一个。
     *
     * @param provider 接入点
     * @param model    模型
     * @param request  请求
     * @param listener 回调
     */
    private void streamWithResilience(ModelProviderDO provider, ModelDO model,
                                      ChatRequest request, ChatListener listener) {
        // 已经产出过内容就不能再重试了 —— 重复内容会被拼进用户的流
        AtomicBoolean produced = new AtomicBoolean(false);
        breakers.executeStream(provider.getPublicId(),
                () -> adapterFactory.getAdapter(provider.getType())
                        .stream(provider, model.getModelName(), request, tracking(listener, produced)),
                produced::get);
    }

    /**
     * 包一层回调，记录「有没有产出过内容」。
     *
     * <p>文本与工具调用都算产出 —— 工具调用是上游明确表达的意图，重试会丢掉它。
     */
    private ChatListener tracking(ChatListener listener, AtomicBoolean produced) {
        return new ChatListener() {
            @Override
            public void onText(String delta) {
                produced.set(true);
                listener.onText(delta);
            }

            @Override
            public void onToolCall(ChatToolCall call) {
                produced.set(true);
                listener.onToolCall(call);
            }

            @Override
            public void onUsage(ChatUsage usage) {
                listener.onUsage(usage);
            }

            @Override
            public void onFinish(String finishReason) {
                listener.onFinish(finishReason);
            }
        };
    }

    /** 在途计数的键。 */
    static String inflightKey(Long userId, String providerPublicId) {
        return INFLIGHT_KEY_PREFIX + userId + ":" + providerPublicId;
    }
}
