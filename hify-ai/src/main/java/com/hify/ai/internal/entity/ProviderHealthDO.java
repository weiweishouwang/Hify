package com.hify.ai.internal.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 接入点的最近健康观测，与配置表一对一，独立读写、不缓存。
 *
 * <p>以 provider_id 为主键，不继承 BaseEntity，不包含逻辑删除或创建者字段。
 * 计数递增、乱序保护和状态判定由后续的数据访问方法及业务层负责。
 */
@TableName("ai_model_provider_health")
public class ProviderHealthDO {

    /** 接入点主键，由调用方提供，不生成独立的自增 ID。 */
    @TableId(value = "provider_id", type = IdType.INPUT)
    private Long providerId;

    /** 可用性：UNKNOWN / UP / DOWN，不代表启停或熔断状态。 */
    @TableField("status")
    private String status;

    /** 连续失败次数，不等同于熔断器滑动窗口的失败次数。 */
    @TableField("fail_count")
    private Integer failCount;

    /** 最近一次观测的总耗时，单位毫秒，不是首字耗时。 */
    @TableField("latency_ms")
    private Long latencyMs;

    /** 最近成功时间（UTC）。 */
    @TableField("last_success_at")
    private LocalDateTime lastSuccessAt;

    /** 最近失败时间（UTC）。 */
    @TableField("last_failure_at")
    private LocalDateTime lastFailureAt;

    /** 规范化错误原因码。 */
    @TableField("error_code")
    private String errorCode;

    /** 脱敏后的最近错误摘要，不保存上游原始错误响应体。 */
    @TableField("error_message")
    private String errorMessage;

    /** 最近有效观测的开始时间（UTC），用于拒绝乱序覆盖。 */
    @TableField("last_observed_at")
    private LocalDateTime lastObservedAt;

    /** 观测来源：probe / invocation。 */
    @TableField("observation_source")
    private String observationSource;

    /** 本次观测实际使用的模型主键。 */
    @TableField("observed_model_id")
    private Long observedModelId;

    /** 本次观测使用的接入点配置版本。 */
    @TableField("provider_config_version")
    private Long providerConfigVersion;

    /** 健康快照更新时间（UTC），独立于接入点配置的 updated_at。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

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
