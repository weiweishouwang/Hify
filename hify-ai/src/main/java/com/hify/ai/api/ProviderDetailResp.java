package com.hify.ai.api;

/** 配置详情与最新健康快照的组合；组合对象本身不缓存。 */
public class ProviderDetailResp {

    /** 配置及关联的 modelConfigs。 */
    private ProviderConfigResp provider;

    /** 每次查询独立读取的健康快照。 */
    private ProviderHealthResp providerHealth;

    public ProviderConfigResp getProvider() {
        return provider;
    }

    public void setProvider(ProviderConfigResp provider) {
        this.provider = provider;
    }

    public ProviderHealthResp getProviderHealth() {
        return providerHealth;
    }

    public void setProviderHealth(ProviderHealthResp providerHealth) {
        this.providerHealth = providerHealth;
    }

}
