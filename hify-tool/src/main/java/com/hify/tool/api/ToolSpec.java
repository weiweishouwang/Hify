package com.hify.tool.api;

import java.util.Map;

/**
 * 工具的元数据。
 *
 * @param toolKey     来源内唯一的工具标识
 * @param name        工具名，进 function calling 的 function name
 * @param description 工具描述，给模型看
 * @param schema      入参 JSON Schema，给模型看
 * @param config      来源相关配置，按 sourceType 分化
 * @param sourceType  来源字面量，见 {@link ToolSourceType}
 */
public record ToolSpec(
        String toolKey,
        String name,
        String description,
        Map<String, Object> schema,
        Map<String, Object> config,
        String sourceType) {
}
