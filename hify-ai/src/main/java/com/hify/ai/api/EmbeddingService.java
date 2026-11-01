package com.hify.ai.api;

import java.util.List;

/**
 * 向量化的公开契约。
 *
 * <p><b>纯能力层</b>：只做「给我文本，返回向量」，不做检索、不做上下文拼接 ——
 * 那些是 knowledge 与编排层的事（CLAUDE.md §2.1 的模块不变量）。
 */
public interface EmbeddingService {

    /**
     * 用指定模型把一批文本向量化。
     *
     * <p>调用是阻塞的（要等上游返回），HTTP 入口应提交到 {@code llmExecutor}。
     *
     * @param modelPublicId 模型的对外标识（{@code ai_model.public_id}）
     * @param texts         待向量化的文本
     * @return 与 {@code texts} 等长、等顺序的向量列表
     */
    List<float[]> embed(String modelPublicId, List<String> texts);
}
