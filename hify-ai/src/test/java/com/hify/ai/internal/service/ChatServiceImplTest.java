package com.hify.ai.internal.service;

import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import com.hify.ai.internal.mapper.ModelProviderMapper;
import com.hify.ai.internal.spi.ChatAdapterFactory;
import com.hify.ai.internal.spi.ChatProtocolAdapter;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.shared.redis.RateLimiter;
import com.hify.shared.resilience.CircuitBreakerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ChatServiceImpl} 的韧性接线。
 *
 * <p>三件事：每用户每 provider 的在途上限、按接入点独立的熔断、只吐内容前才重试。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ModelMapper models;

    @Mock
    private ModelProviderMapper providers;

    @Mock
    private ChatAdapterFactory adapterFactory;

    @Mock
    private ChatProtocolAdapter adapter;

    @Mock
    private CircuitBreakerService breakers;

    @Mock
    private RateLimiter rateLimiter;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(models, providers, adapterFactory, breakers, rateLimiter);
        // 熔断器是 mock：让它把真正要执行的 Runnable 跑起来。
        // 用 lenient：有些用例（模型不存在、参数错误）根本走不到熔断器那一步。
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(breakers).executeStream(anyString(), any(Runnable.class), any());
        // 默认拿到在途名额；测超限的用例自己覆盖
        lenient().when(rateLimiter.tryAcquireInFlight(anyString(), any(Integer.class), any(Duration.class)))
                .thenReturn(true);
    }

    private ChatRequest request(Long userId) {
        ChatRequest request = new ChatRequest();
        request.setModelPublicId("01MODEL");
        request.setUserId(userId);
        request.setMessages(List.of(ChatMessage.user("你好")));
        return request;
    }

    private void stubModel() {
        ModelDO model = new ModelDO();
        model.setPublicId("01MODEL");
        model.setModelName("gpt-4o");
        model.setModelType("chat");
        model.setProviderId(7L);
        model.setEnabled(true);
        when(models.selectOne(any())).thenReturn(model);
    }

    private void stubProvider() {
        ModelProviderDO provider = new ModelProviderDO();
        provider.setPublicId("01PROVIDER");
        provider.setType("openai");
        provider.setEnabled(true);
        when(providers.selectById(7L)).thenReturn(provider);
    }

    private void stubAdapter() {
        when(adapterFactory.getAdapter("openai")).thenReturn(adapter);
    }

    private void stubModelAndProvider() {
        stubModel();
        stubProvider();
        stubAdapter();
    }

    @Test
    @DisplayName("正常路径：占一个在途名额、调用模型、释放名额")
    void acquiresAndReleasesInflightSlot() {
        stubModelAndProvider();

        service.stream(request(42L), new NoopListener());

        verify(rateLimiter).tryAcquireInFlight(eq("hify:llm:inflight:42:01PROVIDER"), eq(3), any(Duration.class));
        verify(rateLimiter).releaseInFlight("hify:llm:inflight:42:01PROVIDER");
        verify(adapter).stream(any(), eq("gpt-4o"), any(), any());
    }

    @Test
    @DisplayName("在途已达上限时直接拒绝，不调模型")
    void rejectsWhenInflightLimitReached() {
        stubModel();
        stubProvider();
        when(rateLimiter.tryAcquireInFlight(anyString(), eq(3), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.stream(request(42L), new NoopListener()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.TOO_MANY_REQUESTS));

        verify(adapter, never()).stream(any(), anyString(), any(), any());
        verify(rateLimiter, never()).releaseInFlight(anyString());
    }

    @Test
    @DisplayName("没有用户上下文时不限流（后台任务不该被算到某个人头上）")
    void skipsRateLimitWithoutUser() {
        stubModelAndProvider();

        service.stream(request(null), new NoopListener());

        verify(rateLimiter, never()).tryAcquireInFlight(anyString(), any(Integer.class), any(Duration.class));
        verify(adapter).stream(any(), eq("gpt-4o"), any(), any());
    }

    @Test
    @DisplayName("熔断器的粒度是接入点实例，不是协议档位")
    void breakerKeyIsProviderInstance() {
        stubModelAndProvider();

        service.stream(request(42L), new NoopListener());

        verify(breakers).executeStream(eq("01PROVIDER"), any(Runnable.class), any());
    }

    @Test
    @DisplayName("模型不存在时报 404")
    void modelNotFound() {
        when(models.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.stream(request(42L), new NoopListener()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("模型标识为空时参数错误")
    void blankModelId() {
        ChatRequest request = new ChatRequest();
        request.setMessages(List.of(ChatMessage.user("你好")));

        assertThatThrownBy(() -> service.stream(request, new NoopListener()))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PARAM_ERROR));
    }

    /** 什么都不做的回调。 */
    private static final class NoopListener implements ChatListener {
        @Override
        public void onText(String delta) {
        }

        @Override
        public void onToolCall(com.hify.ai.api.ChatToolCall call) {
        }

        @Override
        public void onFinish(String finishReason) {
        }
    }
}
