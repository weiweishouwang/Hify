package com.hify.ai.api;

/**
 * token 用量。
 *
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param totalTokens      总 token
 */
public record ChatUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    /** 两个数都是空时返回 null —— 上游没给用量就等于没给。 */
    public static ChatUsage of(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null && completionTokens == null) {
            return null;
        }
        int prompt = promptTokens == null ? 0 : promptTokens;
        int completion = completionTokens == null ? 0 : completionTokens;
        return new ChatUsage(prompt, completion, prompt + completion);
    }
}
