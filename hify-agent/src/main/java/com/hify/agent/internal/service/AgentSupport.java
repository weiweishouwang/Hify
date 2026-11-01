package com.hify.agent.internal.service;

import com.hify.agent.api.AgentParamConfig;
import com.hify.agent.api.AgentStatus;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Agent 配置的常量与校验。 */
public final class AgentSupport {

    /** 初始配置版本。 */
    public static final long INITIAL_VERSION = 1L;

    /** 一期只做 Chatflow 语义。 */
    public static final String APP_TYPE_CHAT = "chat";

    /** 采样温度下限。 */
    public static final double MIN_TEMPERATURE = 0.0d;

    /** 采样温度上限。 */
    public static final double MAX_TEMPERATURE = 2.0d;

    /** 核采样下限。 */
    public static final double MIN_TOP_P = 0.0d;

    /** 核采样上限。 */
    public static final double MAX_TOP_P = 1.0d;

    /** 单次输出 token 下限。 */
    public static final int MIN_MAX_TOKENS = 1;

    /** 单次输出 token 上限。 */
    public static final int MAX_MAX_TOKENS = 200_000;

    private AgentSupport() {
    }

    /** 参数错误。 */
    public static BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /** 名称。 */
    public static String name(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw invalid("名称不能为空");
        }
        if (trimmed.length() > 128) {
            throw invalid("名称最长 128 字符");
        }
        return trimmed;
    }

    /** 描述。 */
    public static String description(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > 512) {
            throw invalid("描述最长 512 字符");
        }
        return trimmed;
    }

    /** 系统提示词。 */
    public static String prompt(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() > 65535) {
            throw invalid("提示词过长");
        }
        return value;
    }

    /**
     * 状态。
     *
     * @param value    状态字面量；为空时给默认草稿
     * @param fallback 默认状态
     * @return 归一化后的状态字面量
     */
    public static String status(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return AgentStatus.fromCode(value.trim()).code();
    }

    /**
     * 引用 id 列表：去空白、去重、保持顺序。
     *
     * <p>去重是必要的 —— 同一个工具绑两次没有意义，
     * 而 JSON 数组里的重复项会让「绑了几个」的统计与实际不符。
     */
    public static List<String> ids(List<String> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String item : value) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    /**
     * 参数区：校验范围后转成要落库的 JSON。
     *
     * @param config 参数区；为空时得到空 Map
     * @return 落库用的参数 Map，没有设置过的参数不出现
     */
    public static Map<String, Object> paramConfig(AgentParamConfig config) {
        if (config == null) {
            return Map.of();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (config.getTemperature() != null) {
            double temperature = config.getTemperature();
            if (temperature < MIN_TEMPERATURE || temperature > MAX_TEMPERATURE) {
                throw invalid("temperature 要在 " + MIN_TEMPERATURE + " 到 " + MAX_TEMPERATURE + " 之间");
            }
            result.put("temperature", temperature);
        }
        if (config.getMaxTokens() != null) {
            int maxTokens = config.getMaxTokens();
            if (maxTokens < MIN_MAX_TOKENS || maxTokens > MAX_MAX_TOKENS) {
                throw invalid("max_tokens 要在 " + MIN_MAX_TOKENS + " 到 " + MAX_MAX_TOKENS + " 之间");
            }
            result.put("max_tokens", maxTokens);
        }
        if (config.getTopP() != null) {
            double topP = config.getTopP();
            if (topP < MIN_TOP_P || topP > MAX_TOP_P) {
                throw invalid("top_p 要在 " + MIN_TOP_P + " 到 " + MAX_TOP_P + " 之间");
            }
            result.put("top_p", topP);
        }
        return result;
    }

    /**
     * 把落库的参数 Map 还原成参数区。
     *
     * @param stored 落库的参数 Map
     * @return 参数区；一个参数都没设时是 null
     */
    public static AgentParamConfig toParamConfig(Map<String, Object> stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        AgentParamConfig config = new AgentParamConfig();
        Object temperature = stored.get("temperature");
        if (temperature instanceof Number number) {
            config.setTemperature(number.doubleValue());
        }
        Object maxTokens = stored.get("max_tokens");
        if (maxTokens instanceof Number number) {
            config.setMaxTokens(number.intValue());
        }
        Object topP = stored.get("top_p");
        if (topP instanceof Number number) {
            config.setTopP(number.doubleValue());
        }
        return config;
    }
}
