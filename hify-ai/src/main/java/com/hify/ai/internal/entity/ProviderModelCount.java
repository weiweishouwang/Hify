package com.hify.ai.internal.entity;

/** 当前页接入点的模型分组统计，不映射独立业务表。 */
public record ProviderModelCount(Long providerId, Long enabledModelCount) {
}
