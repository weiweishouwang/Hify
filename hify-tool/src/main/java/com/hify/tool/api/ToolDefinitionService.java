package com.hify.tool.api;

import java.util.List;

/**
 * 工具定义的存取契约。
 *
 * <p>管理台用：列出工具、查看详情、创建 / 更新 / 删除自定义 HTTP 工具、触发目录同步。
 * 工具的<b>执行</b>走 {@link ToolCatalog}，不在本接口里。
 */
public interface ToolDefinitionService {

    /**
     * 创建一个自定义 HTTP 工具。
     *
     * @param request 创建请求
     * @return 创建后的工具详情
     */
    ToolDetailResp create(ToolCreateReq request);

    /**
     * 按游标分页列出工具。
     *
     * @param query 查询条件
     * @return 一页工具
     */
    ToolListResp list(ToolListQuery query);

    /**
     * 按对外 ID 批量取工具。
     *
     * <p><b>给跨模块引用解析用</b>：别的模块在自己的表里存工具的对外 ID
     * （如 {@code ai_agent.tool_ids}），组装时要按 id 取出工具规格去拼 function
     * calling 的 tools 定义。逐个 {@link #get(String)} 是 N+1 次跨模块调用，
     * 而 {@link #list(ToolListQuery)} 是游标分页、一次拿不全。
     *
     * <p><b>停用的工具也会返回</b>（{@code enabled} 为 false）——
     * 调用方要能区分「不存在」与「已停用」，两者的提示不一样。
     *
     * @param publicIds 工具对外 ID 集合；为空时返回空列表
     * @return 查到的目录行；查不到的 ID 不出现在结果里
     */
    List<ToolBinding> listBindingsByPublicIds(List<String> publicIds);

    /**
     * 取工具详情。
     *
     * @param publicId 工具的对外 ID
     * @return 工具详情；请求头里的敏感值已脱敏
     */
    ToolDetailResp get(String publicId);

    /**
     * 更新工具。
     *
     * @param publicId 工具的对外 ID
     * @param request  更新请求
     * @return 更新后的工具详情
     */
    ToolDetailResp update(String publicId, ToolUpdateReq request);

    /**
     * 删除工具（逻辑删除）。内置与 MCP 来源的目录行由同步机制托管，不接受手工删除。
     *
     * @param publicId 工具的对外 ID
     */
    void delete(String publicId);

    /**
     * 从各来源重新发现工具并同步进目录。
     *
     * @return 同步统计
     */
    ToolSyncResp sync();
}
