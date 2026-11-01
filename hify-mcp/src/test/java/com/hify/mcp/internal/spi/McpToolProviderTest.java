package com.hify.mcp.internal.spi;

import com.hify.mcp.internal.client.McpClientManager;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.mcp.internal.mapper.McpServerMapper;
import com.hify.mcp.internal.service.McpToolDiscovery;
import com.hify.shared.exception.BizException;
import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** MCP 来源的工具发现与调用。 */
@ExtendWith(MockitoExtension.class)
class McpToolProviderTest {

    @Mock McpToolDiscovery discovery;
    @Mock McpServerMapper servers;
    @Mock McpClientManager clients;

    McpToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new McpToolProvider(discovery, servers, clients);
    }

    static McpServerDO server() {
        var server = new McpServerDO();
        server.setId(1L);
        server.setPublicId("01MCPSERVER00000000000000001");
        server.setName("文件服务");
        server.setTransport("http");
        server.setEnabled(true);
        return server;
    }

    @Test
    @DisplayName("工具标识是「服务标识:上游工具名」，跨服务不撞名")
    void toolKeyIsPrefixed() {
        var key = McpToolProvider.key("01MCPSERVER00000000000000001", "search");
        assertThat(key).isEqualTo("01MCPSERVER00000000000000001:search");
        assertThat(McpToolProvider.serverIdOf(key)).isEqualTo("01MCPSERVER00000000000000001");
        assertThat(McpToolProvider.toolNameOf(key)).isEqualTo("search");
    }

    @Test
    @DisplayName("标识格式不合法时拒绝")
    void rejectsMalformedToolKey() {
        assertThatThrownBy(() -> McpToolProvider.serverIdOf("no-separator"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> McpToolProvider.toolNameOf("01ABC:"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("列工具：上游工具转成统一目录的条目")
    void listToolsConvertsToSpecs() {
        var tool = McpSchema.Tool.builder("search", Map.of("type", "object")).build();
        when(discovery.discoverAll()).thenReturn(List.of(new McpToolDiscovery.Discovery(
                "01MCPSERVER00000000000000001", "文件服务", true, List.of(tool), null, "2025-06-18")));

        var specs = provider.listTools();

        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).toolKey()).isEqualTo("01MCPSERVER00000000000000001:search");
        assertThat(specs.get(0).name()).isEqualTo("search");
        assertThat(specs.get(0).sourceType()).isEqualTo("mcp");
    }

    @Test
    @DisplayName("调用工具：按标识里的服务标识找到服务并转发")
    void invokeRoutesToServer() {
        var server = server();
        when(servers.selectOne(any())).thenReturn(server);
        when(clients.callTool(eq(server), eq("search"), any())).thenReturn(
                McpSchema.CallToolResult.builder(List.of(McpSchema.TextContent.builder("找到 3 个文件").build())).build());
        when(clients.toText(any())).thenReturn("找到 3 个文件");

        var result = provider.invoke(null, "01MCPSERVER00000000000000001:search", Map.of("q", "手册"));

        assertThat(result.success()).isTrue();
        assertThat(result.content()).contains("找到 3 个文件");
    }

    @Test
    @DisplayName("上游报错的结果转成失败")
    void invokeReportsUpstreamError() {
        var server = server();
        when(servers.selectOne(any())).thenReturn(server);
        when(clients.callTool(eq(server), eq("search"), any())).thenReturn(
                McpSchema.CallToolResult.builder(List.of(McpSchema.TextContent.builder("参数不对").build()))
                        .isError(true).build());
        when(clients.toText(any())).thenReturn("参数不对");

        var result = provider.invoke(null, "01MCPSERVER00000000000000001:search", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("参数不对");
    }

    @Test
    @DisplayName("服务不存在或已停用时拒绝调用")
    void invokeRejectsUnavailableServer() {
        when(servers.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> provider.invoke(null, "01MCPSERVER00000000000000001:search", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).kind())
                .isEqualTo(ToolErrorKind.TOOL_ERROR);

        var disabled = server();
        disabled.setEnabled(false);
        when(servers.selectOne(any())).thenReturn(disabled);
        assertThatThrownBy(() -> provider.invoke(null, "01MCPSERVER00000000000000001:search", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("已停用");
    }
}
