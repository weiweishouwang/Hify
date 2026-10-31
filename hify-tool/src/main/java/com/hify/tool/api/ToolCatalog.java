package com.hify.tool.api;

import java.util.List;
import java.util.Map;

/**
 * 跨模块的工具目录。
 *
 * <p>编排层（conversation / workflow）通过本接口取工具、执行工具，<b>不直接依赖任何来源模块</b>。
 * 本接口是 {@link ToolProvider} 集合的聚合视图。
 */
public interface ToolCatalog {

    /**
     * 列出所有可用（已启用）的工具。
     *
     * @return 工具元数据列表
     */
    List<ToolSpec> listTools();

    /**
     * 执行一个工具。
     *
     * @param context    调用上下文
     * @param sourceType 来源字面量
     * @param toolKey    来源内唯一的工具标识
     * @param args       模型给出的入参
     * @return 执行结果
     */
    ToolResult invoke(ToolInvokeContext context, String sourceType, String toolKey, Map<String, Object> args);
}
