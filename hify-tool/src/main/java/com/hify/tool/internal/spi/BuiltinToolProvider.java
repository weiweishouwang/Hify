package com.hify.tool.internal.spi;

import com.hify.tool.api.ToolErrorKind;
import com.hify.tool.api.ToolExecutionException;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolProvider;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSourceType;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.internal.service.ToolConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 内置工具的来源实现。
 *
 * <p>收集所有 {@link BuiltinTool} 实现，把它们暴露成 {@link ToolProvider}。
 * 工具的元数据来自代码，目录表里对应的那几行由同步机制托管。
 */
@Component
public class BuiltinToolProvider implements ToolProvider {

    private final List<BuiltinTool> tools;

    public BuiltinToolProvider(List<BuiltinTool> tools) {
        this.tools = tools;
    }

    @Override
    public String sourceType() {
        return ToolSourceType.BUILTIN.code();
    }

    @Override
    public List<ToolSpec> listTools() {
        return tools.stream()
                .map(tool -> new ToolSpec(
                        tool.toolKey(),
                        tool.name(),
                        tool.description(),
                        tool.schema(),
                        Map.of(ToolConfig.PROVIDER_BEAN, tool.getClass().getSimpleName()),
                        sourceType()))
                .toList();
    }

    @Override
    public ToolResult invoke(ToolInvokeContext context, String toolKey, Map<String, Object> args) {
        BuiltinTool tool = tools.stream()
                .filter(candidate -> candidate.toolKey().equals(toolKey))
                .findFirst()
                .orElseThrow(() -> new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "内置工具不存在: " + toolKey));
        try {
            return ToolResult.ok(tool.execute(args == null ? Map.of() : args));
        } catch (RuntimeException exception) {
            throw new ToolExecutionException(ToolErrorKind.TOOL_ERROR, "内置工具执行失败: " + toolKey, exception);
        }
    }
}
