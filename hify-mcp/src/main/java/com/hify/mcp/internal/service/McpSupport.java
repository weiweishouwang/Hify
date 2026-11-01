package com.hify.mcp.internal.service;

import com.hify.mcp.api.McpTransport;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

/** MCP 服务配置的常量与校验。 */
public final class McpSupport {

    /** 连通状态：还没探测过。 */
    public static final String STATUS_UNKNOWN = "unknown";

    /** 连通状态：连得上。 */
    public static final String STATUS_ONLINE = "online";

    /** 连通状态：连不上。 */
    public static final String STATUS_OFFLINE = "offline";

    /** 初始配置版本。 */
    public static final long INITIAL_VERSION = 1L;

    private McpSupport() {
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 名称。 */
    public static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("名称不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("名称最长 128 字符");
        }
        return trimmed;
    }

    /**
     * 按传输方式校验并归一化配置。
     *
     * @param transport 传输方式
     * @param command   stdio 的启动命令
     * @param url       http 的服务地址
     * @return 归一化后的传输方式
     */
    public static McpTransport transport(String transport, String command, String url) {
        McpTransport resolved = McpTransport.fromCode(transport);
        if (resolved == McpTransport.STDIO) {
            if (command == null || command.isBlank()) {
                throw invalid("stdio 传输必须配置启动命令");
            }
            return resolved;
        }
        if (url == null || url.isBlank()) {
            throw invalid("http 传输必须配置服务地址");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException exception) {
            throw invalid("服务地址格式不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid("服务地址只支持 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("服务地址缺少主机名");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("服务地址不能包含用户名或密码");
        }
        if (uri.getRawFragment() != null) {
            throw invalid("服务地址不能包含片段");
        }
        return resolved;
    }

    /** 服务地址：去掉末尾斜杠。 */
    public static String url(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 启动命令。 */
    public static String command(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 命令参数。 */
    public static List<String> args(List<String> value) {
        return value == null || value.isEmpty() ? List.of() : List.copyOf(value);
    }

    /** 环境变量。 */
    public static Map<String, String> env(Map<String, String> value) {
        return value == null || value.isEmpty() ? Map.of() : Map.copyOf(value);
    }

    /** 请求头。 */
    public static Map<String, String> headers(Map<String, String> value) {
        return value == null || value.isEmpty() ? Map.of() : Map.copyOf(value);
    }
}
