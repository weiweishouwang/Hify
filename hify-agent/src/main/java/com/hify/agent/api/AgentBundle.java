package com.hify.agent.api;

import com.hify.ai.api.ModelSummaryResp;
import com.hify.knowledge.api.KnowledgeBaseSummaryResp;
import com.hify.tool.api.ToolSpec;

import java.util.List;

/**
 * 组装好的 Agent —— 编排层拿去就能跑。
 *
 * <p><b>只是一份零件清单</b>：本模块不执行推理、不调用工具、不检索知识库
 * （CLAUDE.md §2.1 的不变量）。真正的调用由 conversation / workflow 发起。
 *
 * @param publicId       Agent 对外标识
 * @param name           名称
 * @param status         状态；组装出来的一定是 published
 * @param promptTemplate 系统提示词
 * @param model          主模型
 * @param tools          绑定的工具规格，可直接进 function calling 的 tools 定义
 * @param knowledgeBases 绑定的知识库
 * @param paramConfig    参数区
 */
public record AgentBundle(
        String publicId,
        String name,
        String status,
        String promptTemplate,
        ModelSummaryResp model,
        List<ToolSpec> tools,
        List<KnowledgeBaseSummaryResp> knowledgeBases,
        AgentParamConfig paramConfig) {
}
