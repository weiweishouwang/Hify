package com.hify.ai.api;

/** 列表的实时聚合行；只在缓存外构造，健康及模型计数不进入配置缓存。 */
public class ProviderListItemResp extends ProviderSummaryResp {
    private ProviderHealthResp providerHealth;
    private Long enabledModelCount;

    public ProviderHealthResp getProviderHealth() {
        return providerHealth;
    }

    public void setProviderHealth(ProviderHealthResp providerHealth) {
        this.providerHealth = providerHealth;
    }

    public Long getEnabledModelCount() {
        return enabledModelCount;
    }

    public void setEnabledModelCount(Long enabledModelCount) {
        this.enabledModelCount = enabledModelCount;
    }
}
