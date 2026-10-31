package com.hify.tool.internal.spi;

import java.util.Map;

/**
 * 内置工具。
 *
 * <p>内置工具是<b>代码注册</b>的：随应用发布，不落库配置，随应用发布。
 * 实现类用 {@code @Component} 注册，{@link BuiltinToolProvider} 注入
 * {@code List<BuiltinTool>} 收集。
 */
public interface BuiltinTool {

    /** 来源内唯一的工具标识。 */
    String toolKey();

    /** 工具名，进 function calling 的 function name。 */
    String name();

    /** 工具描述，给模型看。 */
    String description();

    /** 入参 JSON Schema，给模型看。 */
    Map<String, Object> schema();

    /**
     * 执行工具。
     *
     * @param args 模型给出的入参
     * @return 返回给模型的内容
     */
    String execute(Map<String, Object> args);
}
