package com.hify.mcp.api;

import java.time.LocalDateTime;
import java.util.Map;

/** MCP 服务摘要：不含请求头密文，也不含工具缓存。 */
public class McpServerSummaryResp {

    /** 对外服务标识。 */
    private String publicId;

    /** 名称，全局唯一。 */
    private String name;

    /** 传输方式：stdio / http。 */
    private String transport;

    /** stdio 的启动命令。 */
    private String command;

    /** stdio 的命令参数。 */
    private java.util.List<String> args;

    /** stdio 的环境变量。 */
    private Map<String, String> env;

    /** http 传输的服务地址。 */
    private String url;

    /** http 传输的请求头；敏感值以占位符替换。 */
    private Map<String, String> headers;

    /** 是否启用。 */
    private Boolean enabled;

    /** 连通状态：unknown / online / offline。 */
    private String status;

    /** 最近一次探测时间(UTC)。 */
    private LocalDateTime lastCheckedAt;

    /** 工具缓存时间(UTC)。 */
    private LocalDateTime toolCacheAt;

    /** 缓存里发现的工具数。 */
    private Integer toolCount;

    /** 服务配置版本，用于乐观锁。 */
    private Long configVersion;

    /** 创建时间(UTC)。 */
    private LocalDateTime createdAt;

    /** 更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public java.util.List<String> getArgs() {
        return args;
    }

    public void setArgs(java.util.List<String> args) {
        this.args = args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public LocalDateTime getToolCacheAt() {
        return toolCacheAt;
    }

    public void setToolCacheAt(LocalDateTime toolCacheAt) {
        this.toolCacheAt = toolCacheAt;
    }

    public Integer getToolCount() {
        return toolCount;
    }

    public void setToolCount(Integer toolCount) {
        this.toolCount = toolCount;
    }

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
