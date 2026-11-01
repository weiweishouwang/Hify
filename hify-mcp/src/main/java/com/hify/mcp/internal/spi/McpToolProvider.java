package com.hify.mcp.internal.spi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.mcp.internal.client.McpClientManager;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.mcp.internal.mapper.McpServerMapper;
import com.hify.mcp.internal.service.McpToolDiscovery;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolProvider;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 来源的工具提供者。
 *
 * <p><b>实现的是 tool 模块 api 包里的 SPI</b> —— 方向是 {@code mcp → tool}。
 * tool 模块不依赖本模块，工具通过 {@code sourceType='mcp'} 进统一目录。
 *
 * <p><b>工具标识带服务前缀</b>：{@code <serverPublicId>:<上游工具名>}。不同 MCP 服务
 * 可能提供同名工具，前缀保证 {@code tool_definition} 里的 {@code tool_key} 唯一，
 * 也让 {@link #invoke} 不必反查目录就知道该连哪个服务。
 */
@Component
public class McpToolProvider implements ToolProvider {

    /** 本来源在统一目录里的字面量。 */
    public static final String SOURCE_TYPE = "mcp";

    /** 工具标识里服务标识与工具名的分隔符。ULID 里不会出现这个字符。 */
    private static final String SEPARATOR = ":";

    private final McpToolDiscovery discovery;
    private final McpServerMapper servers;
    private final McpClientManager clients;

    public McpToolProvider(McpToolDiscovery discovery, McpServerMapper servers, McpClientManager clients) {
        this.discovery = discovery;
        this.servers = servers;
        this.clients = clients;
    }

    @Override
    public String sourceType() {
        return SOURCE_TYPE;
    }

    /**
     * MCP 的目录行由同步器托管：最后一个 MCP 服务被删掉时，
     * 它的工具行要跟着停用（否则目录里留着调不通的工具）。
     */
    @Override
    public boolean managesDirectoryRows() {
        return true;
    }

    @Override
    public List<ToolSpec> listTools() {
        List<ToolSpec> specs = new ArrayList<>();
        for (var result : discovery.discoverAll()) {
            for (var tool : result.tools()) {
                specs.add(new ToolSpec(
                        key(result.serverPublicId(), tool.name()),
                        tool.name(),
                        tool.description(),
                        tool.inputSchema(),
                        Map.of("mcpServerPublicId", result.serverPublicId()),
                        SOURCE_TYPE));
            }
        }
        return specs;
    }

    @Override
    public ToolResult invoke(ToolInvokeContext context, String toolKey, Map<String, Object> args) {
        String serverPublicId = serverIdOf(toolKey);
        String toolName = toolNameOf(toolKey);
        McpServerDO server = servers.selectOne(new QueryWrapper<McpServerDO>()
                .eq("public_id", serverPublicId));
        if (server == null) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "MCP 服务不存在: " + serverPublicId);
        }
        if (!Boolean.TRUE.equals(server.getEnabled())) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "MCP 服务已停用: " + server.getName());
        }
        try {
            var result = clients.callTool(server, toolName, args);
            if (result != null && Boolean.TRUE.equals(result.isError())) {
                return ToolResult.failure(clients.toText(result));
            }
            return ToolResult.ok(clients.toText(result));
        } catch (RuntimeException exception) {
            throw new ToolExecutionException(ToolErrorKind.UPSTREAM_ERROR,
                    "MCP 工具调用失败: " + toolName, exception);
        }
    }

    /** 工具标识 → 服务标识。 */
    static String serverIdOf(String toolKey) {
        int index = toolKey == null ? -1 : toolKey.indexOf(SEPARATOR);
        if (index <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP 工具标识格式不合法: " + toolKey);
        }
        return toolKey.substring(0, index);
    }

    /** 工具标识 → 上游工具名。 */
    static String toolNameOf(String toolKey) {
        int index = toolKey == null ? -1 : toolKey.indexOf(SEPARATOR);
        if (index < 0 || index >= toolKey.length() - 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP 工具标识格式不合法: " + toolKey);
        }
        return toolKey.substring(index + 1);
    }

    static String key(String serverPublicId, String toolName) {
        return serverPublicId + SEPARATOR + toolName;
    }
}
