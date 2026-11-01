package com.hify.ai.api;

import java.util.Map;

/**
 * 一个可供模型调用的工具。
 *
 * @param name        工具名，进 function calling 的 function name
 * @param description 工具描述，给模型看
 * @param schema      入参 JSON Schema，给模型看
 */
public record ChatTool(String name, String description, Map<String, Object> schema) {
}
