package com.hify.workflow.internal.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code {{变量}}} 模板解析。
 *
 * <p><b>不引表达式语言</b> —— 表达式引擎要么多一个依赖，要么自己写解析器，
 * 两种都在「只支持 JSON 配置」的降级定位里过重（CLAUDE.md §1.2）。
 *
 * <p>变量名允许字母、数字、下划线、点和短横线：
 * <ul>
 *   <li>{@code input.<字段名>} —— 本次运行的入参</li>
 *   <li>{@code <节点的 output 变量名>} —— 上游节点的输出</li>
 * </ul>
 *
 * <p><b>解析不到的变量原样保留</b>（不报错、不替换成空串）——
 * 提示词里正好有 {@code {{}}` 这种东西时不该把整轮工作流带崩。
 */
@Component
public class TemplateResolver {

    /** 变量占位符。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    /**
     * 解析字符串模板。
     *
     * <p>整个模板就是一个占位符时，返回变量的<b>原始值</b>（不是字符串化）——
     * 这样工具入参里的数字还是数字。
     *
     * @param template  模板；为空时原样返回
     * @param variables 变量表
     * @return 解析结果
     */
    public String resolve(String template, Map<String, Object> variables) {
        Object value = resolveValue(template, variables);
        return stringify(value);
    }

    /**
     * 解析任意值：字符串走模板替换，其它值原样返回。
     *
     * @param value     原值
     * @param variables 变量表
     * @return 解析结果
     */
    public Object resolveValue(Object value, Map<String, Object> variables) {
        if (!(value instanceof String text)) {
            return value;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        if (matcher.matches()) {
            Object resolved = lookup(matcher.group(1), variables);
            return resolved == null ? text : resolved;
        }
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            Object resolved = lookup(matcher.group(1), variables);
            // 解析不到就保留原样 —— 提示词里正好有 {{}} 不该把整轮带崩
            String replacement = resolved == null ? matcher.group() : stringify(resolved);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 查变量。
     *
     * @param name      变量名
     * @param variables 变量表
     * @return 变量值；没有时为 null
     */
    public Object lookup(String name, Map<String, Object> variables) {
        if (name == null || name.isBlank() || variables == null) {
            return null;
        }
        int dot = name.indexOf('.');
        if (dot < 0) {
            return variables.get(name);
        }
        String head = name.substring(0, dot);
        String tail = name.substring(dot + 1);
        Object root = variables.get(head);
        if (root instanceof Map<?, ?> map) {
            return map.get(tail);
        }
        return null;
    }

    /** 值转字符串：列表按换行拼，null 转空串。 */
    public static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            StringBuilder result = new StringBuilder();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append('\n');
                }
                result.append(item);
            }
            return result.toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.toString();
        }
        return String.valueOf(value);
    }
}
