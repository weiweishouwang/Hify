package com.hify.conversation.internal.service;

import com.hify.agent.api.AgentBundle;
import com.hify.ai.api.ChatTool;
import com.hify.tool.api.ToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具的「函数名」映射。
 *
 * <p><b>模型只认函数名</b> —— function calling 协议里没有工具 id，
 * 模型回的是函数名，我们得靠它找回是哪个工具。
 *
 * <p><b>函数名必须是 ASCII 的 {@code [a-zA-Z0-9_-]} 且不超过 64 字符</b>
 * （OpenAI 与 Anthropic 都这么要求），而工具名是用户随手起的，可能是中文 ——
 * 所以要清洗一次。清洗后为空（全是中文）就退回 {@code tool}。
 *
 * <p><b>函数名由工具标识（{@code 来源:工具标识}）派生</b>，不是由显示名派生：
 * 显示名可以随时改，工具标识不能 —— 改了它历史消息里的函数名就对不上了。
 */
public final class ToolFunctionNames {

    /** 函数名最长 64 字符，留 10 个字符给去重后缀。 */
    private static final int MAX_NAME_LENGTH = 54;

    /** 工具标识（来源 + 工具标识）的分隔符。 */
    public static final String TOOL_KEY_SEPARATOR = ":";

    private final Map<String, String> functionNameByToolKey;
    private final Map<String, ToolSpec> specByFunctionName;

    private ToolFunctionNames(Map<String, String> functionNameByToolKey,
                              Map<String, ToolSpec> specByFunctionName) {
        this.functionNameByToolKey = functionNameByToolKey;
        this.specByFunctionName = specByFunctionName;
    }

    /**
     * 按 Agent 绑定的工具建映射。
     *
     * @param bundle Agent 的组装结果
     * @return 映射
     */
    public static ToolFunctionNames of(AgentBundle bundle) {
        Map<String, String> byToolKey = new LinkedHashMap<>();
        Map<String, ToolSpec> byFunctionName = new LinkedHashMap<>();
        List<ToolSpec> tools = bundle == null ? null : bundle.tools();
        if (tools == null) {
            return new ToolFunctionNames(byToolKey, byFunctionName);
        }
        for (ToolSpec spec : tools) {
            String toolKey = toolKey(spec);
            if (byToolKey.containsKey(toolKey)) {
                // 同一个来源里不会有重复的工具标识；真撞了就跳过，别覆盖
                continue;
            }
            String functionName = uniqueName(sanitize(spec.toolKey()), byFunctionName);
            byToolKey.put(toolKey, functionName);
            byFunctionName.put(functionName, spec);
        }
        return new ToolFunctionNames(byToolKey, byFunctionName);
    }

    /**
     * 工具标识（来源:工具标识）→ 函数名。
     *
     * @param bundle Agent 的组装结果
     * @return 映射
     */
    public static Map<String, String> byToolKey(AgentBundle bundle) {
        return of(bundle).functionNameByToolKey;
    }

    /** 送给模型的工具定义。 */
    public List<ChatTool> chatTools() {
        List<ChatTool> result = new ArrayList<>(specByFunctionName.size());
        specByFunctionName.forEach((name, spec) -> result.add(
                new ChatTool(name, spec.description() == null ? "" : spec.description(),
                        spec.schema())));
        return result;
    }

    /** 函数名 → 工具规格。 */
    public ToolSpec specOf(String functionName) {
        return specByFunctionName.get(functionName);
    }

    /** 工具标识（来源:工具标识）→ 函数名。 */
    public String functionNameOf(String toolKey) {
        return functionNameByToolKey.get(toolKey);
    }

    /** 工具的稳定标识。 */
    public static String toolKey(ToolSpec spec) {
        return spec.sourceType() + TOOL_KEY_SEPARATOR + spec.toolKey();
    }

    /** 清洗成合法的函数名。 */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_-]", "_").replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+", "").replaceAll("_+$", "");
        if (cleaned.isEmpty()) {
            return "tool";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "t_" + cleaned;
        }
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    /** 保证函数名唯一。 */
    private static String uniqueName(String base, Map<String, ToolSpec> taken) {
        if (!taken.containsKey(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String candidate = base + "_" + suffix;
            if (!taken.containsKey(candidate)) {
                return candidate;
            }
        }
        return base + "_" + System.nanoTime();
    }
}
