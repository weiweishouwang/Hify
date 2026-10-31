package com.hify.ai.internal.spi;

import com.hify.ai.internal.entity.ModelProviderDO;

import java.util.List;

/**
 * 协议适配器：把一类接入点协议的差异收敛在一处。
 *
 * <p>每个实现只关心三件事：**目录端点路径**、**鉴权请求头**、**目录响应的字段结构**。
 * 调度、服务层都不再按 type 分支。
 *
 * <p>接口放在 {@code internal/spi} 而不是 {@code api}：入参是数据对象，
 * 跨模块契约层不允许出现数据对象（CLAUDE.md §3.2）。
 */
public interface ProviderAdapter {

    /** 协议档位，如 openai / anthropic / ollama。 */
    String type();

    /**
     * 只读探测端点与鉴权是否可用。
     *
     * <p>不发起模型推理，也不调用业务熔断器 —— 已熔断的接入点仍要能探测恢复。
     *
     * @param provider 接入点配置
     * @throws com.hify.shared.http.LlmApiException 上游不可达、鉴权失败或响应结构无效
     * @throws com.hify.shared.exception.BizException 配置本身不合法
     */
    void testConnection(ModelProviderDO provider);

    /**
     * 列出上游可用的模型标识。
     *
     * @param provider 接入点配置
     * @return 上游模型标识；上游未返回任何模型时是空列表
     */
    List<String> listModels(ModelProviderDO provider);
}
