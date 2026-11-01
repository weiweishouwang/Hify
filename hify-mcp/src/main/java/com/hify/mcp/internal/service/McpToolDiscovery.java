package com.hify.mcp.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.mcp.internal.client.McpClientManager;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.mcp.internal.mapper.McpServerMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从各个 MCP 服务发现工具。
 *
 * <p><b>发现与登记是一条路径</b>：这里实时调 {@code tools/list} 并顺手把结果写回
 * {@code mcp_server.tool_cache}；tool 模块的目录同步器再把这些工具登记进
 * {@code tool_definition}。管理台点「同步」时一条链路走完，立即生效。
 *
 * <p><b>单个服务失败不影响其它</b>：逐个 try/catch，失败的记一笔就跳过。
 */
@Service
public class McpToolDiscovery {

    private static final Logger log = LoggerFactory.getLogger(McpToolDiscovery.class);

    private final McpServerMapper servers;
    private final McpClientManager clients;

    public McpToolDiscovery(McpServerMapper servers, McpClientManager clients) {
        this.servers = servers;
        this.clients = clients;
    }

    /**
     * 发现所有启用服务的工具。
     *
     * @return 逐个服务的发现结果
     */
    public List<Discovery> discoverAll() {
        List<McpServerDO> enabled = servers.selectList(new QueryWrapper<McpServerDO>()
                .eq("enabled", true).orderByAsc("id"));
        List<Discovery> results = new ArrayList<>();
        for (McpServerDO server : enabled) {
            results.add(discover(server));
        }
        return results;
    }

    /**
     * 发现一个服务的工具。
     *
     * @param server 服务配置
     * @return 发现结果；连不上时 {@code connected=false}
     */
    public Discovery discover(McpServerDO server) {
        try {
            var client = clients.client(server);
            var initialization = client.getCurrentInitializationResult();
            List<McpSchema.Tool> tools = clients.listTools(server);
            writeCache(server, tools);
            return new Discovery(server.getPublicId(), server.getName(), true, tools, null,
                    initialization == null ? null : initialization.protocolVersion());
        } catch (RuntimeException exception) {
            markOffline(server);
            String message = describe(exception);
            log.warn("MCP 服务发现工具失败 name={} reason={}", server.getName(),
                    exception.getClass().getSimpleName());
            return new Discovery(server.getPublicId(), server.getName(), false, List.of(), message, null);
        }
    }

    /** 把工具写进缓存，供管理台展示。 */
    private void writeCache(McpServerDO server, List<McpSchema.Tool> tools) {
        List<Map<String, Object>> cache = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            cache.add(clients.toCacheEntry(tool));
        }
        McpServerDO update = new McpServerDO();
        update.setId(server.getId());
        update.setToolCache(cache);
        update.setToolCacheAt(LocalDateTime.now(ZoneOffset.UTC));
        update.setStatus(McpSupport.STATUS_ONLINE);
        update.setLastCheckedAt(LocalDateTime.now(ZoneOffset.UTC));
        servers.updateById(update);
    }

    private void markOffline(McpServerDO server) {
        McpServerDO update = new McpServerDO();
        update.setId(server.getId());
        update.setStatus(McpSupport.STATUS_OFFLINE);
        update.setLastCheckedAt(LocalDateTime.now(ZoneOffset.UTC));
        servers.updateById(update);
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "连接失败";
        }
        return "连接失败：" + message;
    }

    /**
     * 一次发现的结果。
     *
     * @param serverPublicId 服务标识
     * @param serverName     服务名
     * @param connected      是否连得上
     * @param tools          发现的工具
     * @param message        连不上时的原因
     * @param protocolVersion 协商出的协议版本
     */
    public record Discovery(String serverPublicId, String serverName, boolean connected,
                            List<McpSchema.Tool> tools, String message, String protocolVersion) {
    }
}
