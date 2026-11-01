package com.hify.agent.api;

/**
 * Agent 的参数区。
 *
 * <p><b>只认这三个参数</b> —— 上游对不认识的参数是<b>静默丢弃</b>而不是报错
 * （CLAUDE.md §3.5），放开任意参数等于允许用户填一个上游根本不认的值，
 * 然后困惑于「为什么没生效」。
 *
 * <p>取值范围在业务层校验，超范围直接拒绝保存。
 */
public class AgentParamConfig {

    /** 采样温度，0 – 2。 */
    private Double temperature;

    /** 单次输出 token 上限。 */
    private Integer maxTokens;

    /** 核采样，0 – 1。 */
    private Double topP;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }
}
