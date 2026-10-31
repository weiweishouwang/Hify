package com.hify.ai.internal.entity;

import java.time.LocalDateTime;

/** 一次探测的持久化参数；开始时间用于拒绝乱序覆盖，结束时间用于成功/失败时间。 */
public record ProviderHealthObservation(Long providerId, Long configVersion,
                                        LocalDateTime startedAt, LocalDateTime finishedAt,
                                        long latencyMs, boolean successful,
                                        String errorCode, String errorMessage) {
}
