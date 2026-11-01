package com.hify.mcp.internal.converter;

import com.hify.mcp.api.McpServerSummaryResp;
import com.hify.mcp.internal.entity.McpServerDO;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MCP 服务的数据对象到对外 DTO 的转换。 */
@Component
public class McpConverter {

    private final SensitiveHeaderCipher headerCipher;

    public McpConverter(SensitiveHeaderCipher headerCipher) {
        this.headerCipher = headerCipher;
    }

    /**
     * 服务摘要：请求头里的敏感值以占位符替换，密文与明文都不出服务端。
     */
    public McpServerSummaryResp toSummary(McpServerDO row) {
        McpServerSummaryResp resp = new McpServerSummaryResp();
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setTransport(row.getTransport());
        resp.setCommand(row.getCommand());
        resp.setArgs(row.getArgs());
        resp.setEnv(row.getEnv());
        resp.setUrl(row.getUrl());
        resp.setHeaders(maskHeaders(row.getHeaders()));
        resp.setEnabled(row.getEnabled());
        resp.setStatus(row.getStatus());
        resp.setConfigVersion(row.getConfigVersion());
        resp.setLastCheckedAt(row.getLastCheckedAt());
        resp.setToolCacheAt(row.getToolCacheAt());
        resp.setToolCount(row.getToolCache() == null ? 0 : row.getToolCache().size());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    private Map<String, String> maskHeaders(Map<String, Object> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        return headerCipher.maskHeaders(headers);
    }

    /** 工具缓存里的工具名列表。 */
    public List<String> toolNames(List<Map<String, Object>> toolCache) {
        if (toolCache == null) {
            return List.of();
        }
        return toolCache.stream()
                .map(tool -> tool.get("name"))
                .filter(name -> name != null)
                .map(String::valueOf)
                .toList();
    }

    /** 构造一条工具缓存。 */
    public Map<String, Object> toolCacheEntry(String name, String description, Map<String, Object> schema) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("description", description);
        entry.put("inputSchema", schema == null ? Map.of() : schema);
        return entry;
    }
}
