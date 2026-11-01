package com.hify.tool.api;

/**
 * 目录行与它的工具规格。
 *
 * <p>给跨模块引用解析用：别的模块在自己的表里存工具的对外 ID
 * （如 {@code ai_agent.tool_ids}），需要按 id 拿回「它还在不在、启用没有、规格是什么」。
 *
 * @param publicId 工具对外 ID
 * @param enabled  是否启用
 * @param spec     工具规格，可直接进 function calling 的 tools 定义
 */
public record ToolBinding(String publicId, boolean enabled, ToolSpec spec) {
}
