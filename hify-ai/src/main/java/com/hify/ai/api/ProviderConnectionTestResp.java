package com.hify.ai.api;

/**
 * 一次手动端点连通性测试的结果，不携带凭据或供应商原始响应。
 *
 * <p><b>测两层</b>（CLAUDE.md §3.5）：
 * <ul>
 *   <li>{@link #connected} —— 协议连通：最小请求通不通</li>
 *   <li>{@link #capabilitySupported} —— 能力连通：带一个 tools 定义跑一轮，
 *       看能不能回出 tool_calls</li>
 * </ul>
 * 只测第一层，Ollama 的 {@code /v1} 兼容层会显示「正常」，
 * 但它把 tools 定义悄悄丢了 —— Agent 的工具循环是坏的。
 */
public class ProviderConnectionTestResp {
    private Boolean connected;
    private Long latencyMs;

    /** 能力连通是否通过；null 表示没测（比如接入点下没有启用的对话模型）。 */
    private Boolean capabilitySupported;

    /** 能力连通的说明：为什么没测 / 为什么失败。 */
    private String capabilityMessage;

    /** 能力连通测的是哪个模型。 */
    private String capabilityModel;

    /** 能力连通的耗时（一次真实的模型调用，通常是秒级）。 */
    private Long capabilityLatencyMs;

    public Boolean getConnected() {
        return connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Boolean getCapabilitySupported() {
        return capabilitySupported;
    }

    public void setCapabilitySupported(Boolean capabilitySupported) {
        this.capabilitySupported = capabilitySupported;
    }

    public String getCapabilityMessage() {
        return capabilityMessage;
    }

    public void setCapabilityMessage(String capabilityMessage) {
        this.capabilityMessage = capabilityMessage;
    }

    public String getCapabilityModel() {
        return capabilityModel;
    }

    public void setCapabilityModel(String capabilityModel) {
        this.capabilityModel = capabilityModel;
    }

    public Long getCapabilityLatencyMs() {
        return capabilityLatencyMs;
    }

    public void setCapabilityLatencyMs(Long capabilityLatencyMs) {
        this.capabilityLatencyMs = capabilityLatencyMs;
    }
}
