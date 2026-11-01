package com.hify.mcp.internal.client;

import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端的连接池。
 *
 * <p><b>按服务缓存长连接</b>：stdio 传输每次新建客户端都要 spawn 一个进程（几百毫秒起），
 * 工具调用的延迟不可接受；HTTP 传输每次都要重新 initialize。
 *
 * <p><b>空闲回收</b>：超过 {@link #IDLE_TIMEOUT} 没被用的连接会被关掉，下次调用重建。
 * 服务配置改了（{@code config_version} 变了）也会重建 —— 旧连接连的是老地址。
 *
 * <p><b>并发</b>：每个服务一把锁，不同服务互不阻塞；同服务的并发调用共用一个客户端
 * （MCP 协议本身支持并发请求）。
 */
@Component
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    /** 空闲多久回收连接。 */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    /** 单次请求超时。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** 连接超时。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 初始化超时。 */
    private static final Duration INITIALIZATION_TIMEOUT = Duration.ofSeconds(15);

    /** {@code tools/list} 最多翻多少页，防止上游异常导致无限翻页。 */
    public static final int MAX_TOOL_PAGES = 20;

    private final Map<String, CachedClient> clients = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final SensitiveHeaderCipher headerCipher;

    public McpClientManager(SensitiveHeaderCipher headerCipher) {
        this.headerCipher = headerCipher;
    }

    /**
     * 取一个可用的客户端，没有就建一个。
     *
     * @param server 服务配置
     * @return 已初始化的客户端
     */
    public McpSyncClient client(McpServerDO server) {
        String id = server.getPublicId();
        Object lock = locks.computeIfAbsent(id, key -> new Object());
        synchronized (lock) {
            CachedClient cached = clients.get(id);
            if (cached != null && cached.usable(server)) {
                cached.touch();
                return cached.client();
            }
            if (cached != null) {
                closeQuietly(cached.client());
            }
            McpSyncClient client = build(server);
            clients.put(id, new CachedClient(client, server.getConfigVersion(), System.currentTimeMillis()));
            return client;
        }
    }

    /**
     * 列出上游提供的工具（跟完分页）。
     *
     * @param server 服务配置
     * @return 工具列表
     */
    public List<McpSchema.Tool> listTools(McpServerDO server) {
        McpSyncClient client = client(server);
        List<McpSchema.Tool> tools = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_TOOL_PAGES; page++) {
            McpSchema.ListToolsResult result = cursor == null ? client.listTools() : client.listTools(cursor);
            if (result == null || result.tools() == null) {
                break;
            }
            tools.addAll(result.tools());
            cursor = result.nextCursor();
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        }
        return tools;
    }

    /**
     * 调用一个工具。
     *
     * @param server   服务配置
     * @param toolName 上游的工具名
     * @param args     入参
     * @return 上游返回的结果
     */
    public McpSchema.CallToolResult callTool(McpServerDO server, String toolName, Map<String, Object> args) {
        McpSyncClient client = client(server);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder(toolName)
                .arguments(args == null ? Map.of() : args)
                .build();
        return client.callTool(request);
    }

    /** 断开并忘掉某个服务的连接。服务被删或改了配置时调用。 */
    public void evict(String publicId) {
        CachedClient cached = clients.remove(publicId);
        if (cached != null) {
            closeQuietly(cached.client());
        }
        locks.remove(publicId);
    }

    /** 应用停机时关掉所有连接 —— stdio 的子进程否则会成孤儿。 */
    @PreDestroy
    public void closeAll() {
        clients.forEach((id, cached) -> closeQuietly(cached.client()));
        clients.clear();
    }

    // ── 内部方法 ────────────────────────────────────────────────────

    private McpSyncClient build(McpServerDO server) {
        McpClientTransport transport = createTransport(server);
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(INITIALIZATION_TIMEOUT)
                .build();
        client.initialize();
        log.info("MCP 客户端已连接 name={} transport={}", server.getName(), server.getTransport());
        return client;
    }

    private McpClientTransport createTransport(McpServerDO server) {
        String transport = server.getTransport();
        if ("stdio".equals(transport)) {
            if (server.getCommand() == null || server.getCommand().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "stdio 传输必须配置启动命令");
            }
            ServerParameters.Builder builder = ServerParameters.builder(server.getCommand());
            if (server.getArgs() != null && !server.getArgs().isEmpty()) {
                builder.args(server.getArgs());
            }
            if (server.getEnv() != null && !server.getEnv().isEmpty()) {
                builder.env(server.getEnv());
            }
            return new StdioClientTransport(builder.build(), McpJsonDefaults.getMapper());
        }
        if ("http".equals(transport)) {
            if (server.getUrl() == null || server.getUrl().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "http 传输必须配置服务地址");
            }
            Map<String, String> headers = plainHeaders(server);
            return HttpClientStreamableHttpTransport.builder(server.getUrl())
                    .connectTimeout(CONNECT_TIMEOUT)
                    .httpRequestCustomizer(new McpSyncHttpClientRequestCustomizer() {
                        @Override
                        public void customize(HttpRequest.Builder request, String method, URI uri,
                                             String body, McpTransportContext context) {
                            headers.forEach(request::header);
                        }
                    })
                    .build();
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "不支持的 MCP 传输方式: " + transport);
    }

    /** 解密后的请求头。 */
    public Map<String, String> plainHeaders(McpServerDO server) {
        if (server.getHeaders() == null || server.getHeaders().isEmpty()) {
            return Map.of();
        }
        return headerCipher.decryptHeaders(server.getHeaders());
    }

    /** 把上游工具转成可缓存的普通 Map（不直接存 SDK 的类型，避免 Jackson 2/3 混用）。 */
    public Map<String, Object> toCacheEntry(McpSchema.Tool tool) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", tool.name());
        entry.put("description", tool.description());
        entry.put("inputSchema", tool.inputSchema() == null ? Map.of() : tool.inputSchema());
        return entry;
    }

    /** 把上游返回的内容拼成给模型看的文本。 */
    public String toText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent && textContent.text() != null) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(textContent.text());
            }
        }
        return text.toString();
    }

    private static void closeQuietly(McpSyncClient client) {
        try {
            client.close();
        } catch (RuntimeException exception) {
            log.warn("关闭 MCP 客户端失败", exception);
        }
    }

    /**
     * 缓存的客户端：连同它建立时的配置版本与最后使用时间。
     *
     * <p>配置变了（{@code config_version} 递增）或空闲太久，这个客户端就不再可用 ——
     * 前者连的是老地址，后者白白占着一个连接（stdio 传输还占着一个子进程）。
     */
    private static final class CachedClient {

        private final McpSyncClient client;
        private final Long configVersion;
        private volatile long lastUsedAt;

        CachedClient(McpSyncClient client, Long configVersion, long lastUsedAt) {
            this.client = client;
            this.configVersion = configVersion;
            this.lastUsedAt = lastUsedAt;
        }

        McpSyncClient client() {
            return client;
        }

        void touch() {
            this.lastUsedAt = System.currentTimeMillis();
        }

        boolean usable(McpServerDO server) {
            if (!Objects.equals(configVersion, server.getConfigVersion())) {
                return false;
            }
            return System.currentTimeMillis() - lastUsedAt < IDLE_TIMEOUT.toMillis();
        }
    }
}
