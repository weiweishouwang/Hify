package com.hify.ai.internal.spi;

import com.hify.ai.api.ChatListener;
import com.hify.ai.api.ChatRequest;
import com.hify.ai.internal.entity.ModelProviderDO;

/**
 * 对话协议的适配器：把「怎么跟模型说话」这件事收敛在一处。
 *
 * <p>和 {@link ProviderAdapter}（目录适配）并列，都是按协议档位分发的策略。
 * 三套协议的请求体形状、工具定义形状、工具调用形状、流式分帧方式全都不同 ——
 * 调用方（{@code ChatServiceImpl}）只认统一的 {@link ChatListener} 回调。
 *
 * <p>接口放在 {@code internal/spi}：入参含数据对象，契约层不允许出现数据对象
 * （CLAUDE.md §3.2）。
 */
public interface ChatProtocolAdapter {

    /** 协议档位，如 openai / anthropic / ollama。 */
    String type();

    /**
     * 发一次流式对话请求，把上游的增量归一化后回调出去。
     *
     * <p><b>阻塞到整条流结束</b>，回调跑在调用方线程上。
     *
     * @param provider  接入点配置
     * @param modelName 上游模型标识，原样发送
     * @param request   对话请求
     * @param listener  增量回调
     * @throws com.hify.shared.http.LlmApiException 上游不可达、鉴权失败或响应结构无效
     */
    void stream(ModelProviderDO provider, String modelName, ChatRequest request, ChatListener listener);
}
