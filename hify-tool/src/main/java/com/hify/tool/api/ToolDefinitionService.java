package com.hify.tool.api;

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
