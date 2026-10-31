package com.hify.ai.api;

import java.time.LocalDateTime;

/** 实时读取的健康快照，不进入 provider-cache。 */
public class ProviderHealthResp {

    /** 健康状态，不等同于启停或熔断状态。 */
    private String status;

    /** 连续失败次数。 */
    private Integer failCount;

    /** 最近请求总耗时(毫秒)。 */
    private Long latencyMs;

    /** 最近成功时间(UTC)。 */
    private LocalDateTime lastSuccessAt;

    /** 最近失败时间(UTC)。 */
    private LocalDateTime lastFailureAt;

    /** 规范化错误原因。 */
    private String errorCode;

    /** 脱敏错误摘要。 */
    private String errorMessage;

    /** 最近观测时间(UTC)。 */
    private LocalDateTime lastObservedAt;

    /** 观测来源。 */
    private String observationSource;

    /** 观测使用的模型。 */
    private Long observedModelId;

    /** 观测使用的配置版本。 */
    private Long providerConfigVersion;

    /** 快照更新时间(UTC)。 */
    private LocalDateTime updatedAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public LocalDateTime getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(LocalDateTime lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public LocalDateTime getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(LocalDateTime lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getLastObservedAt() {
        return lastObservedAt;
    }

    public void setLastObservedAt(LocalDateTime lastObservedAt) {
        this.lastObservedAt = lastObservedAt;
    }

    public String getObservationSource() {
        return observationSource;
    }

    public void setObservationSource(String observationSource) {
        this.observationSource = observationSource;
    }

    public Long getObservedModelId() {
        return observedModelId;
    }

    public void setObservedModelId(Long observedModelId) {
        this.observedModelId = observedModelId;
    }

    public Long getProviderConfigVersion() {
        return providerConfigVersion;
    }

    public void setProviderConfigVersion(Long providerConfigVersion) {
        this.providerConfigVersion = providerConfigVersion;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
