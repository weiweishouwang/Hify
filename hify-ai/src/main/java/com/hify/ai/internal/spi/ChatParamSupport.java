package com.hify.ai.internal.spi;

import com.hify.ai.api.ChatMessage;
import com.hify.ai.api.ChatRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/** 对话参数与消息角色的公共约定。 */
final class ChatParamSupport {

    /** 生成参数的键。 */
    static final String TEMPERATURE = "temperature";
    static final String MAX_TOKENS = "max_tokens";
    static final String TOP_P = "top_p";

    /** Anthropic 必填 max_tokens，没给时的兜底值。 */
    static final int DEFAULT_MAX_TOKENS = 4096;

    private ChatParamSupport() {
    }

    /** 采样温度；没给就是 null（不带这个字段，让上游用自己的默认）。 */
    static Double temperature(ChatRequest request) {
        return number(request, TEMPERATURE);
    }

    /** 核采样；没给就是 null。 */
    static Double topP(ChatRequest request) {
        return number(request, TOP_P);
    }

    /** 输出上限；没给就是 null。 */
    static Integer maxTokens(ChatRequest request) {
        Object value = raw(request, MAX_TOKENS);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    /** 输出上限；没给时给兜底值（Anthropic 必填）。 */
    static int maxTokensOrDefault(ChatRequest request) {
        Integer value = maxTokens(request);
        return value == null ? DEFAULT_MAX_TOKENS : value;
    }

    private static Double number(ChatRequest request, String key) {
        Object value = raw(request, key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Object raw(ChatRequest request, String key) {
        Map<String, Object> params = request == null ? null : request.getParams();
        return params == null ? null : params.get(key);
    }

    /** 系统提示词；没有就是 null。 */
    static String systemPrompt(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return null;
        }
        for (ChatMessage message : request.getMessages()) {
            if ("system".equals(message.getRole())) {
                return message.getContent();
            }
        }
        return null;
    }

    /** 全部消息（含 system）。OpenAI 与 Ollama 把系统提示词也放在 messages 里。 */
    static java.util.List<ChatMessage> allMessages(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return java.util.List.of();
        }
        return request.getMessages();
    }

    /** 非 system 的消息列表。Anthropic 把系统提示词放在顶层字段里。 */
    static java.util.List<ChatMessage> conversation(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return java.util.List.of();
        }
        return request.getMessages().stream()
                .filter(message -> !"system".equals(message.getRole()))
                .toList();
    }

    /** 生成参数的 Map，值是 null 的不写进去。 */
    static Map<String, Object> params(ChatRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        Double temperature = temperature(request);
        Double topP = topP(request);
        Integer maxTokens = maxTokens(request);
        if (temperature != null) {
            result.put(TEMPERATURE, temperature);
        }
        if (topP != null) {
            result.put(TOP_P, topP);
        }
        if (maxTokens != null) {
            result.put(MAX_TOKENS, maxTokens);
        }
        return result;
    }
}
