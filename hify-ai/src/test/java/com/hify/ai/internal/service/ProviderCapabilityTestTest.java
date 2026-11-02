package com.hify.ai.internal.service;

import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.api.ChatService;
import com.hify.ai.api.ChatToolCall;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.mapper.ModelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link ProviderCapabilityTest}。
 *
 * <p>这一层是「连通性测试必须测两层」里的第二层（CLAUDE.md §3.5）：
 * 带一个 tools 定义跑一轮，看能不能回出 tool_calls。
 */
@ExtendWith(MockitoExtension.class)
class ProviderCapabilityTestTest {

    @Mock
    private ModelMapper models;

    @Mock
    private ChatService chat;

    private ProviderCapabilityTest capabilityTest;
    private ModelProviderDO provider;

    @BeforeEach
    void setUp() {
        capabilityTest = new ProviderCapabilityTest(models, chat);
        provider = new ModelProviderDO();
        provider.setId(7L);
        provider.setPublicId("01PROVIDER");
        provider.setType("openai");
    }

    private void stubModel() {
        ModelDO model = new ModelDO();
        model.setId(9L);
        model.setPublicId("01MODEL");
        model.setModelName("gpt-4o");
        model.setModelType("chat");
        model.setProviderId(7L);
        model.setEnabled(true);
        when(models.selectOne(any())).thenReturn(model);
    }

    /** 让模型「回一个工具调用」。 */
    private void respondWithToolCall() {
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onToolCall(new ChatToolCall("call-1", "hify_probe", "{\"ok\":true}"));
            listener.onFinish("tool_calls");
            return null;
        }).when(chat).stream(any(ChatRequest.class), any(ChatListener.class));
    }

    /** 让模型「只回文字」。 */
    private void respondWithText() {
        doAnswer(invocation -> {
            ChatListener listener = invocation.getArgument(1);
            listener.onText("好的");
            listener.onFinish("stop");
            return null;
        }).when(chat).stream(any(ChatRequest.class), any(ChatListener.class));
    }

    @Test
    @DisplayName("模型回出工具调用 → 能力连通通过")
    void supportedWhenToolCalled() {
        stubModel();
        respondWithToolCall();

        var result = capabilityTest.test(provider);

        assertThat(result.supported()).isTrue();
        assertThat(result.model()).isEqualTo("gpt-4o");
        assertThat(result.latencyMs()).isNotNull();
    }

    @Test
    @DisplayName("模型只回文字 → 能力连通不通过（上游静默丢弃了 tools 定义）")
    void unsupportedWhenNoToolCall() {
        stubModel();
        respondWithText();

        var result = capabilityTest.test(provider);

        assertThat(result.supported()).isFalse();
        assertThat(result.message()).contains("没有回出工具调用");
    }

    @Test
    @DisplayName("没有启用的对话模型 → 跳过，不是「不支持」")
    void skippedWhenNoChatModel() {
        when(models.selectOne(any())).thenReturn(null);

        var result = capabilityTest.test(provider);

        assertThat(result.supported()).isNull();
        assertThat(result.message()).contains("跳过能力连通测试");
    }

    @Test
    @DisplayName("调用报错 → 测不了，不等于能力不行")
    void failedWhenCallThrows() {
        stubModel();
        doAnswer(invocation -> {
            throw new com.hify.shared.exception.BizException(
                    com.hify.shared.exception.ErrorCode.UPSTREAM_ERROR, "上游 500");
        }).when(chat).stream(any(ChatRequest.class), any(ChatListener.class));

        var result = capabilityTest.test(provider);

        assertThat(result.supported()).isFalse();
        assertThat(result.message()).contains("能力连通测试失败");
    }
}
