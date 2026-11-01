package com.hify.agent.api;

/**
 * Agent 配置的存取契约（管理台用）。
 *
 * <p>Agent 的<b>执行</b>不在本接口里 —— 组装走 {@link AgentCatalog}，
 * 推理与工具调用在编排层（conversation / workflow）。
 */
public interface AgentService {

    /**
     * 创建 Agent。
     *
     * @param request 创建请求
     * @return 创建后的 Agent 详情
     */
    AgentDetailResp create(AgentCreateReq request);

    /**
     * 按游标分页列出 Agent。
     *
     * @param query 查询条件
     * @return 一页 Agent
     */
    AgentListResp list(AgentListQuery query);

    /**
     * 取 Agent 详情。
     *
     * @param publicId Agent 的对外 ID
     * @return Agent 详情
     */
    AgentDetailResp get(String publicId);

    /**
     * 更新 Agent。
     *
     * @param publicId Agent 的对外 ID
     * @param request  更新请求
     * @return 更新后的 Agent 详情
     */
    AgentDetailResp update(String publicId, AgentUpdateReq request);

    /**
     * 删除 Agent（逻辑删除）。
     *
     * @param publicId Agent 的对外 ID
     */
    void delete(String publicId);

    /**
     * 列出新建 / 编辑 Agent 时可选项：模型、工具、知识库。
     *
     * @return 可选项
     */
    AgentOptions options();
}
