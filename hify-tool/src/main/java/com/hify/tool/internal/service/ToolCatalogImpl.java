package com.hify.tool.internal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;
import com.hify.tool.api.ToolCatalog;
import com.hify.tool.api.ToolInvokeContext;
import com.hify.tool.api.ToolProvider;
import com.hify.tool.api.ToolResult;
import com.hify.tool.api.ToolSpec;
import com.hify.tool.internal.converter.ToolConverter;
import com.hify.tool.internal.entity.ToolDefinitionDO;
import com.hify.tool.internal.mapper.ToolDefinitionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨来源的工具目录。
 *
 * <p>把各 {@link ToolProvider} 聚合成一个对外视图：
 * <ul>
 *   <li>列出工具 —— 读目录表（{@code tool_definition} 里已启用的行），
 *       目录表才是「有哪些工具可以绑给 Agent」的唯一真相来源</li>
 *   <li>执行工具 —— 按 {@code sourceType} 找到对应的 provider</li>
 * </ul>
 *
 * <p><b>不分发 if-else</b>：provider 的注册靠 Spring 注入
 * {@code List<ToolProvider>}，新增来源只要加一个实现类。
 */
@Service
public class ToolCatalogImpl implements ToolCatalog {

    private final Map<String, ToolProvider> providers = new LinkedHashMap<>();
    private final ToolDefinitionMapper tools;
    private final ToolConverter converter;

    public ToolCatalogImpl(List<ToolProvider> providers, ToolDefinitionMapper tools, ToolConverter converter) {
        for (ToolProvider provider : providers) {
            this.providers.put(provider.sourceType(), provider);
        }
        this.tools = tools;
        this.converter = converter;
    }

    @Override
    public List<ToolSpec> listTools() {
        List<ToolDefinitionDO> rows = tools.selectList(new QueryWrapper<ToolDefinitionDO>()
                .eq("enabled", true).orderByAsc("source_type", "tool_key"));
        List<ToolSpec> specs = new ArrayList<>();
        for (ToolDefinitionDO row : rows) {
            specs.add(converter.toSpec(row));
        }
        return specs;
    }

    @Override
    public ToolResult invoke(ToolInvokeContext context, String sourceType, String toolKey,
                             Map<String, Object> args) {
        ToolProvider provider = providers.get(sourceType);
        if (provider == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的工具来源: " + sourceType);
        }
        return provider.invoke(context, toolKey, args);
    }
}
