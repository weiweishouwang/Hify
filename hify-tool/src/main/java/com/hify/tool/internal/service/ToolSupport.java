package com.hify.tool.internal.service;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

/** 工具配置的校验与规范化。业务规则集中在这里，service 与 converter 都走同一套。 */
public final class ToolSupport {

    /** 一期支持的请求方法。 */
    public static final Set<String> METHODS = Set.of("GET", "POST");

    /** 默认请求方法。 */
    public static final String DEFAULT_METHOD = "POST";

    /** 默认超时：30 秒。工具是整轮对话里的一环，不该比整轮还慢。 */
    public static final int DEFAULT_TIMEOUT_MS = 30_000;

    /** 超时上限：120 秒。 */
    public static final int MAX_TIMEOUT_MS = 120_000;

    /** 请求头数量上限，防止把工具配置当成数据通道。 */
    public static final int MAX_HEADERS = 32;

    /** 单个请求头值的长度上限。 */
    public static final int MAX_HEADER_VALUE_LENGTH = 4096;

    private ToolSupport() {
    }

    /** 名称不能为空、不能超长。 */
    public static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("工具名不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("工具名最长 128 字符");
        }
        return trimmed;
    }

    /** 工具标识：只允许字母、数字、下划线与连字符。 */
    public static String toolKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("工具标识不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("工具标识最长 128 字符");
        }
        if (!trimmed.matches("[A-Za-z0-9_-]+")) {
            throw invalid("工具标识只能包含字母、数字、下划线和连字符");
        }
        return trimmed;
    }

    /**
     * 规范化请求地址。
     *
     * <p>只允许 http/https，不允许带查询串、片段或用户信息 —— 查询串里的东西
     * 会进访问日志，密钥不该出现在 URL 上；参数请放进入参里。
     *
     * @param value 原始地址
     * @return 去掉末尾斜杠的地址
     */
    public static String url(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("请求地址不能为空");
        }
        if (trimmed.length() > 512) {
            throw invalid("请求地址最长 512 字符");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException exception) {
            throw invalid("请求地址格式不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid("请求地址只支持 http 或 https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("请求地址缺少主机名");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("请求地址不能包含用户名或密码");
        }
        if (uri.getRawQuery() != null) {
            throw invalid("请求地址不能包含查询参数，参数请放在入参里");
        }
        if (uri.getRawFragment() != null) {
            throw invalid("请求地址不能包含片段");
        }
        String normalized = uri.toString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /** 请求方法，缺省为 POST。 */
    public static String method(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_METHOD;
        }
        String upper = value.trim().toUpperCase();
        if (!METHODS.contains(upper)) {
            throw invalid("请求方法只支持 GET 或 POST");
        }
        return upper;
    }

    /** 超时毫秒数，缺省 30 秒，上限 120 秒。 */
    public static int timeoutMs(Integer value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        if (value < 1000) {
            throw invalid("超时不能小于 1000 毫秒");
        }
        if (value > MAX_TIMEOUT_MS) {
            throw invalid("超时不能大于 " + MAX_TIMEOUT_MS + " 毫秒");
        }
        return value;
    }

    /**
     * 校验请求头。
     *
     * @param headers 原始请求头
     * @return 去掉空值与空白键后的请求头
     */
    public static Map<String, String> headers(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (headers.size() > MAX_HEADERS) {
            throw invalid("请求头最多 " + MAX_HEADERS + " 个");
        }
        Map<String, String> result = new java.util.LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                return;
            }
            String trimmedName = name.trim();
            if (value == null || value.isBlank()) {
                // 空值视为「不设置这个头」
                return;
            }
            if (value.length() > MAX_HEADER_VALUE_LENGTH) {
                throw invalid("请求头 " + trimmedName + " 的值过长");
            }
            result.put(trimmedName, value);
        });
        return result;
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }
}
