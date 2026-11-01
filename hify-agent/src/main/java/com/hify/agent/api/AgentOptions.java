package com.hify.agent.api;

import java.util.List;

/**
 * 新建 / 编辑 Agent 时可选项。
 *
 * <p>「一个 Agent 能选什么」只有 agent 模块知道 —— 它同时依赖 ai、tool、knowledge
 * 三个模块。前端分别打三个接口再拼，既碎又容易漏，所以由本模块聚合一次返回。
 *
 * @param models         可选的对话模型（用途 chat 且已启用）
 * @param tools          可选的工具（统一目录里已启用的行）
 * @param knowledgeBases 可选的知识库（已启用）
 * @param truncated      某个列表超过了单页上限被截断；管理台要提示用户用搜索缩小范围
 */
public record AgentOptions(
        List<ModelOption> models,
        List<ToolOption> tools,
        List<KnowledgeOption> knowledgeBases,
        boolean truncated) {

    /**
     * 可选模型。
     *
     * @param publicId     模型对外 ID
     * @param displayName  展示名
     * @param modelName    上游实际接受的模型标识
     * @param providerName 所属接入点名称
     */
    public record ModelOption(String publicId, String displayName, String modelName, String providerName) {
    }

    /**
     * 可选工具。
     *
     * @param publicId   工具对外 ID
     * @param name       工具名
     * @param sourceType 来源：builtin / http / mcp
     * @param description 工具描述
     */
    public record ToolOption(String publicId, String name, String sourceType, String description) {
    }

    /**
     * 可选知识库。
     *
     * @param publicId   知识库对外 ID
     * @param name       名称
     * @param description 描述
     * @param docCount   文档数
     */
    public record KnowledgeOption(String publicId, String name, String description, Integer docCount) {
    }
}
