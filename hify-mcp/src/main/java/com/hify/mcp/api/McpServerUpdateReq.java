package com.hify.mcp.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 更新 MCP 服务的请求。
 *
 * <p>请求头是完整替换：非敏感头提交什么就是什么；敏感头提交明文 = 替换，
 * 提交 {@code ******} = 保留原值，没提交该键 = 删除。
 */
public class McpServerUpdateReq {

    /** 名称，全局唯一。 */
    @Size(max = 128, message = "名称最长 128 字符")
    private String name;

    /** 传输方式：stdio / http。 */
    @Pattern(regexp = "stdio|http", message = "传输方式只支持 stdio 或 http")
    private String transport;

    /** stdio 的启动命令。 */
    @Size(max = 1024, message = "启动命令最长 1024 字符")
    private String command;

    /** stdio 的命令参数。 */
    private List<String> args;

    /** stdio 的环境变量。 */
    private Map<String, String> env;

    /** http 传输的服务地址。 */
    @Size(max = 512, message = "服务地址最长 512 字符")
    private String url;

    /** http 传输的请求头；完整替换，规则见类注释。 */
    private Map<String, String> headers;

    /** 是否启用；不传表示不改动。 */
    private Boolean enabled;

    /** 服务配置版本，用于乐观锁；不匹配则拒绝写入。 */
    @NotNull(message = "缺少服务配置版本")
    @Positive(message = "服务配置版本必须为正数")
    private Long configVersion;

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

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }
}
