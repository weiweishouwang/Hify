package com.hify.mcp.api;

import java.util.List;

/**
 * MCP 服务的存取契约。
 *
 * <p>服务配置在 MySQL；工具缓存也在 MySQL（{@code mcp_server.tool_cache}），
 * 分段之外不落别的地方。
 */
public interface McpServerService {

    /**
     * 创建一个 MCP 服务。
     *
     * @param request 创建请求
     * @return 创建后的服务
     */
    McpServerSummaryResp create(McpServerCreateReq request);

    /**
     * 按游标分页列出 MCP 服务。
     *
     * @param query 查询条件
     * @return 一页服务
     */
    McpServerListResp list(McpServerListQuery query);

    /**
     * 取服务详情。
     *
     * @param publicId 服务的对外 ID
     * @return 服务摘要
     */
    McpServerSummaryResp get(String publicId);

    /**
     * 更新服务配置。
     *
     * @param publicId 服务的对外 ID
     * @param request  更新请求
     * @return 更新后的服务
     */
    McpServerSummaryResp update(String publicId, McpServerUpdateReq request);

    /**
     * 删除服务（逻辑删除），并断开它的客户端连接。
     *
     * @param publicId 服务的对外 ID
     */
    void delete(String publicId);

    /**
     * 连上去试一次：initialize + tools/list。
     *
     * @param publicId 服务的对外 ID
     * @return 连接测试结果
     */
    McpConnectionTestResp test(String publicId);

    /**
     * 刷新所有启用服务的工具缓存。
     *
     * @return 同步统计
     */
    McpToolSyncResp syncTools();
}
