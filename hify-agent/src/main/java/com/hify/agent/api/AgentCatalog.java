package com.hify.agent.api;

/**
 * 跨模块的 Agent 目录：把一份配置组装成可运行的 Agent。
 *
 * <p>编排层（conversation / workflow）通过本接口取 Agent，<b>不直接读 agent 的表</b>。
 *
 * <p><b>本模块不执行任何东西</b> —— 组装结果只是一份零件清单，
 * 推理、工具调用、知识库检索都由编排层发起（CLAUDE.md §2.1）。
 */
public interface AgentCatalog {

    /**
     * 组装一个可运行的 Agent。
     *
     * @param publicId Agent 的对外 ID
     * @return 组装结果；只有已发布的 Agent 能组装出来
     */
    AgentBundle assemble(String publicId);
}
