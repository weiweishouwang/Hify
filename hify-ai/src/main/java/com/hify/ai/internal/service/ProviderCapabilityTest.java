package com.hify.ai.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatTool;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.api.ChatUsage;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 接入点的**能力连通**测试。
 *
 * <p>CLAUDE.md §3.5 写死了「连通性测试必须测两层」：
 * <ol>
 *   <li><b>协议连通</b> —— 最小请求通不通（拉一次模型目录）</li>
 *   <li><b>能力连通</b> —— 带一个 tools 定义跑一轮，看能不能回出 tool_calls</li>
 * </ol>
 * 只测第一层，Ollama 的 {@code /v1} 兼容层会显示「正常」，
 * 但它把 {@code tools} 悄悄丢了 —— Agent 的工具循环是坏的，用户要到对话时才发现。
 *
 * <p><b>怎么测</b>：给模型一个只有一个布尔参数的探针工具，
 * 并明确要求它调用这个工具。上游回了 tool_calls 才算通过。
 * 模型只是回了一段文字，说明这一层没被上游真正支持。
 *
 * <p><b>测的是真实链路</b> —— 走 {@link ChatService}，跟生产对话同一条路，
 * 顺带把重试、熔断、在途限流都跑一遍。
 */
@Component
public class ProviderCapabilityTest {

    private static final Logger log = LoggerFactory.getLogger(ProviderCapabilityTest.class);

    /** 探针工具名。 */
    static final String PROBE_TOOL_NAME = "hify_probe";

    /** 探针工具的描述 —— 模型靠它判断这个工具是干什么的。 */
    static final String PROBE_TOOL_DESCRIPTION = "连通性测试用的探针工具。调用它表示上游支持 function calling。";

    /** 要求模型调用工具的提示词。 */
    static final String PROBE_PROMPT =
            "你正在做连通性测试。必须调用 " + PROBE_TOOL_NAME + " 工具，参数 ok 填 true。不要用文字回答。";

    /** 模型类型：只有对话模型能测工具调用。 */
    private static final String MODEL_TYPE_CHAT = "chat";

    private final ModelMapper models;
    private final ChatService chat;

    public ProviderCapabilityTest(ModelMapper models, ChatService chat) {
        this.models = models;
        this.chat = chat;
    }

    /**
     * 测一次能力连通。
     *
     * @param provider 接入点
     * @return 测试结果
     */
    public CapabilityResult test(ModelProviderDO provider) {
        ModelDO model = pickChatModel(provider);
        if (model == null) {
            return CapabilityResult.skipped(null, "接入点下没有启用的对话模型，跳过能力连通测试");
        }
        long start = System.nanoTime();
        AtomicBoolean toolCalled = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        try {
            chat.stream(probeRequest(model), new ChatListener() {
                @Override
                public void onText(String delta) {
                    // 文本不说明能力有问题 —— 有些模型会先说一句再调工具
                }

                @Override
                public void onToolCall(ChatToolCall call) {
                    toolCalled.set(true);
                }

                @Override
                public void onUsage(ChatUsage usage) {
                }

                @Override
                public void onFinish(String finishReason) {
                }
            });
        } catch (RuntimeException exception) {
            // 上游报错就是能力测不了，不是能力不行 —— 两者的提示语要分开
            log.warn("能力连通测试失败 provider={} model={} reason={}",
                    provider.getPublicId(), model.getModelName(), exception.getClass().getSimpleName());
            return CapabilityResult.failed(model.getModelName(), messageOf(exception),
                    elapsedMs(start));
        }
        long latency = elapsedMs(start);
        if (toolCalled.get()) {
            return CapabilityResult.supported(model.getModelName(), latency);
        }
        return CapabilityResult.unsupported(model.getModelName(),
                "模型没有回出工具调用：上游可能不支持 function calling，或静默丢弃了 tools 定义", latency);
    }

    /** 探针请求。 */
    private ChatRequest probeRequest(ModelDO model) {
        ChatRequest request = new ChatRequest();
        request.setModelPublicId(model.getPublicId());
        request.setMessages(List.of(ChatMessage.user(PROBE_PROMPT)));
        request.setTools(List.of(new ChatTool(PROBE_TOOL_NAME, PROBE_TOOL_DESCRIPTION, probeSchema())));
        request.setParams(Map.of("max_tokens", 128));
        return request;
    }

    /** 探针工具的入参 schema：只有一个布尔参数。 */
    private Map<String, Object> probeSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("ok", Map.of("type", "boolean", "description", "固定填 true")),
                "required", List.of("ok"));
    }

    /** 挑一个启用的对话模型来测。 */
    private ModelDO pickChatModel(ModelProviderDO provider) {
        return models.selectOne(new QueryWrapper<ModelDO>()
                .eq("provider_id", provider.getId())
                .eq("model_type", MODEL_TYPE_CHAT)
                .eq("enabled", true)
                .orderByAsc("id")
                .last("LIMIT 1"));
    }

    private long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /**
     * 能力连通测试的结果。
     *
     * @param supported 是否支持（null = 没测）
     * @param model     测的哪个模型；没测时为 null
     * @param message   说明：为什么没测 / 为什么失败
     * @param latencyMs 耗时
     */
    public record CapabilityResult(Boolean supported, String model, String message, Long latencyMs) {

        static CapabilityResult supported(String model, long latencyMs) {
            return new CapabilityResult(true, model, "工具调用正常", latencyMs);
        }

        static CapabilityResult unsupported(String model, String message, long latencyMs) {
            return new CapabilityResult(false, model, message, latencyMs);
        }

        static CapabilityResult failed(String model, String message, long latencyMs) {
            return new CapabilityResult(false, model, "能力连通测试失败: " + message, latencyMs);
        }

        static CapabilityResult skipped(String model, String message) {
            return new CapabilityResult(null, model, message, null);
        }
    }
}
