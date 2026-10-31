package com.hify.ai.internal.service;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 接入点输入校验及 JSON 防御性复制，不包含数据库访问。 */
public final class ProviderConfigSupport {
    public static final String OPENAI = "openai";
    public static final String ANTHROPIC = "anthropic";
    public static final String OLLAMA = "ollama";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String API_KEY = "apiKey";
    public static final String ANTHROPIC_VERSION = "anthropicVersion";
    public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";
    public static final long INITIAL_VERSION = 1L;
    private static final Set<String> TYPES = Set.of(OPENAI, ANTHROPIC, OLLAMA);
    private static final Set<String> SECRET_KEYS = Set.of("apikey", "authorization", "proxyauthorization",
            "xapikey", "token", "accesstoken", "refreshtoken", "password", "clientsecret", "secret", "credentials");
    private static final int MAX_JSON_DEPTH = 16;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_URL_LENGTH = 512;

    private ProviderConfigSupport() { }

    public static String name(String name) {
        if (name == null || name.isBlank() || name.strip().length() > MAX_NAME_LENGTH) {
            throw invalid("名称不能为空且不能超过128个字符");
        }
        return name.strip();
    }

    public static String type(String type) {
        if (!TYPES.contains(type == null ? "" : type)) {
            throw invalid("不支持的接入点协议");
        }
        return type;
    }

    public static String baseUrl(String value) {
        try {
            if (value == null || value.isBlank() || value.length() > MAX_URL_LENGTH) {
                throw invalid("接入点地址不能为空且不能超过512个字符");
            }
            URI uri = new URI(value.strip());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw invalid("接入点地址必须为不含凭据、查询参数或片段的 HTTP/HTTPS 地址");
            }
            return value.strip().replaceAll("/+$", "");
        } catch (URISyntaxException exception) {
            throw new BizException(ErrorCode.PARAM_ERROR, "接入点地址格式不合法", exception);
        }
    }

    public static Map<String, Object> copy(Map<String, Object> source) {
        return copyObject(source, 0, false);
    }

    public static Map<String, Object> extraParams(Map<String, Object> source) {
        return copyObject(source, 0, true);
    }

    private static Map<String, Object> copyObject(Map<?, ?> source, int depth, boolean forbidSecrets) {
        checkDepth(depth);
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (var entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalid("JSON 参数名必须是字符串");
            }
            String normalized = key.replaceAll("[-_]", "").toLowerCase(java.util.Locale.ROOT);
            if (forbidSecrets && SECRET_KEYS.contains(normalized)) {
                throw invalid("extraParams 不能包含鉴权凭据");
            }
            result.put(key, copyValue(entry.getValue(), depth + 1, forbidSecrets));
        }
        return result;
    }

    private static Object copyValue(Object value, int depth, boolean forbidSecrets) {
        checkDepth(depth);
        if (value instanceof Map<?, ?> map) {
            return copyObject(map, depth, forbidSecrets);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(copyValue(item, depth + 1, forbidSecrets));
            }
            return result;
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof java.math.BigDecimal
                || value instanceof java.math.BigInteger
                || value instanceof Double number && Double.isFinite(number)
                || value instanceof Float floatNumber && Float.isFinite(floatNumber)) {
            return value;
        }
        throw invalid("扩展参数必须是有效的 JSON 值");
    }

    private static void checkDepth(int depth) {
        if (depth > MAX_JSON_DEPTH) {
            throw invalid("JSON 参数嵌套过深");
        }
    }

    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }
}
