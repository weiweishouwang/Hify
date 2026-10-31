package com.hify.tool.internal.spi;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolProvider;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSourceType;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import com.hify.tool.internal.security.ToolHeaderCipher;
import com.hify.tool.internal.service.ToolConfig;
import com.hify.tool.internal.service.ToolSupport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义 HTTP 工具的来源实现。
 *
 * <p><b>HTTP 来源不由 provider 发现</b> —— 它的工具是数据：用户在管理台创建后
 * 直接落 {@code tool_definition}。所以 {@link #listTools()} 返回空列表，
 * 同步器会跳过这个来源、不做任何下架处理。
 *
 * <p>执行时才从表里读配置：地址、方法、请求头（含解密后的凭据）、超时。
 */
@Component
public class HttpToolProvider implements ToolProvider {

    private final ToolDefinitionMapper tools;
    private final ToolHeaderCipher headerCipher;
    private final ToolHttpClient client;

    public HttpToolProvider(ToolDefinitionMapper tools, ToolHeaderCipher headerCipher, ToolHttpClient client) {
        this.tools = tools;
        this.headerCipher = headerCipher;
        this.client = client;
    }

    @Override
    public String sourceType() {
        return ToolSourceType.HTTP.code();
    }

    @Override
    public List<ToolSpec> listTools() {
        // HTTP 工具是数据不是代码，由用户创建后直接落库，不由这里发现
        return List.of();
    }

    @Override
    public ToolResult invoke(ToolInvokeContext context, String toolKey, Map<String, Object> args) {
        ToolDefinitionDO row = tools.selectOne(new QueryWrapper<ToolDefinitionDO>()
                .eq("source_type", sourceType()).eq("tool_key", toolKey));
        if (row == null) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "工具不存在: " + toolKey);
        }
        if (!Boolean.TRUE.equals(row.getEnabled())) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "工具已停用: " + toolKey);
        }
        Map<String, Object> config = row.getConfig() == null ? Map.of() : row.getConfig();
        String url = string(config.get(ToolConfig.URL));
        String method = string(config.get(ToolConfig.METHOD));
        int timeoutMs = integer(config.get(ToolConfig.TIMEOUT_MS));
        if (url == null || url.isBlank()) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "工具缺少请求地址: " + toolKey);
        }
        Object headers = config.get(ToolConfig.HEADERS);
        Map<String, String> plainHeaders = headers instanceof Map<?, ?> map
                ? headerCipher.decryptHeaders(castMap(map))
                : Map.of();

        var response = client.call(method == null ? ToolSupport.DEFAULT_METHOD : method,
                url, plainHeaders, args, timeoutMs);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("truncated", response.truncated());
        return ToolResult.ok(response.content(), metadata);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return ToolSupport.DEFAULT_TIMEOUT_MS;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return ToolSupport.DEFAULT_TIMEOUT_MS;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
