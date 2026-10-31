package com.hify.tool.api;

import java.util.List;
import java.util.Map;

/**
 * 工具来源的扩展点。
 *
 * <p><b>由消费方（tool）定义、由来源方实现</b> —— code-organization spec §M10 的依赖倒置：
 * {@code builtin} / {@code http} / {@code mcp} 各自实现本接口，tool 注入
 * {@code List<ToolProvider>} 收集。<b>tool 模块不得依赖任何来源模块</b>，
 * 尤其不能依赖 mcp（否则 tool ↔ mcp 成环）。
 *
 * <p>实现类用 {@code @Qualifier} 显式命名注入，不靠 {@code @Primary}。
 */
public interface ToolProvider {

    /**
     * 本来源的标识，取值见 {@link ToolSourceType}。
     *
     * @return 来源字面量
     */
    String sourceType();

    /**
     * 列出本来源当前可发现的工具。
     *
     * <p><b>返回空列表表示本来源不由 provider 发现</b>，同步器会跳过它、不做任何下架处理。
     * HTTP 来源属于这种情况：它的工具是数据（用户在管理台创建后直接落库），不是代码。
     *
     * @return 工具元数据列表
     */
    List<ToolSpec> listTools();

    /**
     * 执行一个工具。
     *
     * @param context 调用上下文，显式携带用户与 traceId
     * @param toolKey  来源内唯一的工具标识
     * @param args     模型给出的入参
     * @return 执行结果；失败时返回 {@link ToolResult#failure(String)} 或抛
     *         {@link ToolExecutionException}
     */
    ToolResult invoke(ToolInvokeContext context, String toolKey, Map<String, Object> args);
}
