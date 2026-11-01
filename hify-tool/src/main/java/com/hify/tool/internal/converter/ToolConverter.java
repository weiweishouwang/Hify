package com.hify.tool.internal.converter;

import com.hify.tool.api.ToolDetailResp;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.api.ToolSummaryResp;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import com.hify.tool.internal.service.ToolConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 工具数据对象与对外 DTO 的转换。转换逻辑不堆在 service 里。 */
@Component
public class ToolConverter {

    private final SensitiveHeaderCipher headerCipher;

    public ToolConverter(SensitiveHeaderCipher headerCipher) {
        this.headerCipher = headerCipher;
    }

    /** 摘要：不含入参 schema，也不含请求头。 */
    public ToolSummaryResp toSummary(ToolDefinitionDO row) {
        ToolSummaryResp resp = new ToolSummaryResp();
        resp.setId(row.getId());
        resp.setPublicId(row.getPublicId());
        resp.setName(row.getName());
        resp.setSourceType(row.getSourceType());
        resp.setToolKey(row.getToolKey());
        resp.setDescription(row.getDescription());
        resp.setEnabled(row.getEnabled());
        resp.setConfigVersion(row.getConfigVersion());
        resp.setCreatedAt(row.getCreatedAt());
        resp.setUpdatedAt(row.getUpdatedAt());
        return resp;
    }

    /**
     * 工具规格：进 function calling 的 tools 定义。
     *
     * <p>不含 {@code publicId} —— 目录行既有对外 ID 也有工具标识，
     * 而 {@link ToolSpec} 是 provider 产出的，provider 只知道工具标识。
     */
    public ToolSpec toSpec(ToolDefinitionDO row) {
        return new ToolSpec(
                row.getToolKey(),
                row.getName(),
                row.getDescription(),
                row.getSchema(),
                row.getConfig(),
                row.getSourceType());
    }

    /**
     * 详情：带入参 schema 与请求头。
     *
     * <p>请求头里的敏感值以 {@code ******} 占位 —— 明文与密文都不出服务端。
     * http 之外的来源没有 url / method / headers，返回 null。
     */
    public ToolDetailResp toDetail(ToolDefinitionDO row) {
        ToolDetailResp resp = new ToolDetailResp();
        ToolSummaryResp summary = toSummary(row);
        resp.setId(summary.getId());
        resp.setPublicId(summary.getPublicId());
        resp.setName(summary.getName());
        resp.setSourceType(summary.getSourceType());
        resp.setToolKey(summary.getToolKey());
        resp.setDescription(summary.getDescription());
        resp.setEnabled(summary.getEnabled());
        resp.setConfigVersion(summary.getConfigVersion());
        resp.setCreatedAt(summary.getCreatedAt());
        resp.setUpdatedAt(summary.getUpdatedAt());

        resp.setSchema(row.getSchema());
        Map<String, Object> config = row.getConfig();
        if (config != null) {
            resp.setUrl(string(config.get(ToolConfig.URL)));
            resp.setMethod(string(config.get(ToolConfig.METHOD)));
            resp.setTimeoutMs(integer(config.get(ToolConfig.TIMEOUT_MS)));
            Object headers = config.get(ToolConfig.HEADERS);
            if (headers instanceof Map<?, ?> map) {
                resp.setHeaders(headerCipher.maskHeaders(castMap(map)));
            }
        }
        return resp;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
