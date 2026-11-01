package com.hify.mcp.internal.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.shared.mybatis.BaseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务的数据对象。
 *
 * <p>{@code headers} 里的敏感值由业务层 AES-GCM 加密后写入，TypeHandler 只负责
 * JSON 转换。{@code toolCache} 存 {@code tools/list} 的结果快照，供管理台展示。
 */
@TableName(value = "mcp_server", autoResultMap = true)
public class McpServerDO extends BaseEntity {

    /** 对外 ID，ULID（Universally Unique Lexicographically Sortable Identifier）。 */
    @TableField("public_id")
    private String publicId;

    /** 名称，全局唯一。 */
    @TableField("name")
    private String name;

    /** 传输方式：stdio / http。 */
    @TableField("transport")
    private String transport;

    /** stdio 的启动命令。 */
    @TableField("command")
    private String command;

    /** stdio 的命令参数。 */
    @TableField(value = "args", typeHandler = JacksonTypeHandler.class)
    private List<String> args;

    /** stdio 的环境变量。 */
    @TableField(value = "env", typeHandler = JacksonTypeHandler.class)
    private Map<String, String> env;

    /** http 传输的服务地址。 */
    @TableField("url")
    private String url;

    /** http 传输的请求头；敏感值已加密。 */
    @TableField(value = "headers", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> headers;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 连通状态：unknown / online / offline。 */
    @TableField("status")
    private String status;

    /** 服务配置版本，用于乐观锁。 */
    @TableField("config_version")
    private Long configVersion;

    /** 最近一次探测时间(UTC)。 */
    @TableField("last_checked_at")
    private LocalDateTime lastCheckedAt;

    /** tools/list 的结果缓存。 */
    @TableField(value = "tool_cache", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> toolCache;

    /** 缓存时间(UTC)。 */
    @TableField("tool_cache_at")
    private LocalDateTime toolCacheAt;

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

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
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

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
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

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public List<Map<String, Object>> getToolCache() {
        return toolCache;
    }

    public void setToolCache(List<Map<String, Object>> toolCache) {
        this.toolCache = toolCache;
    }

    public LocalDateTime getToolCacheAt() {
        return toolCacheAt;
    }

    public void setToolCacheAt(LocalDateTime toolCacheAt) {
        this.toolCacheAt = toolCacheAt;
    }
}
