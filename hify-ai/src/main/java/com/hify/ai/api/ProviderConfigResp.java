package com.hify.ai.api;

import java.util.List;
import java.util.Map;

/** 可缓存的配置详情，不含密钥、密文或健康快照。 */
public class ProviderConfigResp extends ProviderSummaryResp {

    /** 仅包含非敏感的鉴权公共参数。 */
    private Map<String, Object> authConfig;

    /** 接入点扩展参数。 */
    private Map<String, Object> extraParams;

    /** 关联的未删除模型配置，包含已停用模型。 */
    private List<ModelConfigResp> modelConfigs;

    public Map<String, Object> getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(Map<String, Object> authConfig) {
        this.authConfig = authConfig;
    }

    public Map<String, Object> getExtraParams() {
        return extraParams;
    }

    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams;
    }

    public List<ModelConfigResp> getModelConfigs() {
        return modelConfigs;
    }

    public void setModelConfigs(List<ModelConfigResp> modelConfigs) {
        this.modelConfigs = modelConfigs;
    }

}
