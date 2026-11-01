package com.hify.mcp.api;

import java.util.List;

/**
 * 工具同步的统计结果。
 *
 * @param servers   逐个服务的同步明细
 * @param discovered 发现的工具总数
 * @param failed    连接失败的服务数
 */
public record McpToolSyncResp(List<ServerStat> servers, int discovered, int failed) {

    /**
     * 单个服务的同步明细。
     *
     * @param publicId  服务标识
     * @param name      服务名
     * @param connected 是否连得上
     * @param tools     发现的工具数
     * @param message   连不上时的原因
     */
    public record ServerStat(String publicId, String name, boolean connected, int tools, String message) {
    }
}
