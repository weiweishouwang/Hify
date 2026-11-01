package com.hify.mcp.api;

import java.util.List;

/**
 * 连接测试的结果。
 *
 * @param connected  是否连得上
 * @param serverName 上游报告的服务名
 * @param serverVersion 上游报告的版本
 * @param protocolVersion 协商出的协议版本
 * @param toolCount  上游提供的工具数
 * @param toolNames  工具名列表（最多 50 个，避免响应过大）
 * @param message    连不上时的可读原因
 */
public record McpConnectionTestResp(
        boolean connected,
        String serverName,
        String serverVersion,
        String protocolVersion,
        int toolCount,
        List<String> toolNames,
        String message) {
}
