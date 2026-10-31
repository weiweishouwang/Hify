package com.hify.ai.api;

/** 一次手动端点连通性测试的结果，不携带凭据或供应商原始响应。 */
public class ProviderConnectionTestResp {
    private Boolean connected;
    private Long latencyMs;

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
}
