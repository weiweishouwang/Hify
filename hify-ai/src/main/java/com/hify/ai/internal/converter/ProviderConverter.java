package com.hify.ai.internal.converter;

import com.hify.ai.api.ModelConfigResp;
import com.hify.ai.api.ProviderConfigResp;
import com.hify.ai.api.ProviderCreateReq;
import com.hify.ai.api.ProviderHealthResp;
import com.hify.ai.api.ProviderSummaryResp;
import com.hify.ai.internal.entity.ModelDO;
import com.hify.ai.internal.entity.ModelProviderDO;
import com.hify.ai.internal.entity.ProviderHealthDO;
import com.hify.ai.internal.service.ProviderConfigSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** DO/DTO 转换；缓存和对外响应不携带密钥或加密封套。 */
public final class ProviderConverter {
    private ProviderConverter() { }

    public static ProviderSummaryResp toSummary(ModelProviderDO source) {
        var result = new ProviderSummaryResp();
        summary(source, result);
        return result;
    }

    public static ProviderConfigResp toConfig(ModelProviderDO source, List<ModelDO> models) {
        var result = new ProviderConfigResp();
        summary(source, result);
        // 采用白名单，不因后续新增敏感鉴权字段而误回传。
        var auth = new java.util.LinkedHashMap<String, Object>();
        if (source.getAuthConfig() != null
                && source.getAuthConfig().get(ProviderConfigSupport.ANTHROPIC_VERSION) instanceof String version) {
            auth.put(ProviderConfigSupport.ANTHROPIC_VERSION, version);
        }
        result.setAuthConfig(auth);
        result.setExtraParams(ProviderConfigSupport.extraParams(source.getExtraParams()));
        List<ModelConfigResp> modelConfigs = new ArrayList<>();
        for (ModelDO model : models) {
            modelConfigs.add(toModel(model));
        }
        result.setModelConfigs(modelConfigs);
        return result;
    }

    public static ModelProviderDO newProvider(ProviderCreateReq request) {
        var result = new ModelProviderDO();
        result.setName(ProviderConfigSupport.name(request.getName()));
        result.setType(ProviderConfigSupport.type(request.getType()));
        result.setBaseUrl(ProviderConfigSupport.baseUrl(request.getBaseUrl()));
        result.setExtraParams(ProviderConfigSupport.extraParams(request.getExtraParams()));
        result.setEnabled(request.getEnabled() == null || request.getEnabled());
        return result;
    }

    public static ProviderHealthResp toHealth(ProviderHealthDO source, Long configVersion) {
        var result = new ProviderHealthResp();
        result.setStatus(ProviderConfigSupport.UNKNOWN);
        result.setFailCount(0);
        if (source == null) {
            return result;
        }
        result.setStatus(Objects.equals(configVersion, source.getProviderConfigVersion())
                ? source.getStatus() : ProviderConfigSupport.UNKNOWN);
        result.setFailCount(source.getFailCount());
        result.setLatencyMs(source.getLatencyMs());
        result.setLastSuccessAt(source.getLastSuccessAt());
        result.setLastFailureAt(source.getLastFailureAt());
        result.setErrorCode(source.getErrorCode());
        result.setErrorMessage(source.getErrorMessage());
        result.setLastObservedAt(source.getLastObservedAt());
        result.setObservationSource(source.getObservationSource());
        result.setObservedModelId(source.getObservedModelId());
        result.setProviderConfigVersion(source.getProviderConfigVersion());
        result.setUpdatedAt(source.getUpdatedAt());
        return result;
    }

    private static void summary(ModelProviderDO source, ProviderSummaryResp result) {
        result.setId(source.getId());
        result.setPublicId(source.getPublicId());
        result.setName(source.getName());
        result.setType(source.getType());
        result.setBaseUrl(source.getBaseUrl());
        result.setEnabled(source.getEnabled());
        result.setConfigVersion(source.getConfigVersion());
        result.setCredentialsConfigured(source.getAuthConfig() != null
                && source.getAuthConfig().get(ProviderConfigSupport.API_KEY) != null);
        result.setCreatedAt(source.getCreatedAt());
        result.setUpdatedAt(source.getUpdatedAt());
    }

    private static ModelConfigResp toModel(ModelDO source) {
        var result = new ModelConfigResp();
        result.setPublicId(source.getPublicId());
        result.setModelName(source.getModelName());
        result.setDisplayName(source.getDisplayName());
        result.setModelType(source.getModelType());
        result.setCapabilities(ProviderConfigSupport.copy(source.getCapabilities()));
        result.setContextWindow(source.getContextWindow());
        result.setMaxOutputTokens(source.getMaxOutputTokens());
        result.setEmbeddingDimension(source.getEmbeddingDimension());
        result.setDefaultParams(ProviderConfigSupport.copy(source.getDefaultParams()));
        result.setExtraParams(ProviderConfigSupport.extraParams(source.getExtraParams()));
        result.setEnabled(source.getEnabled());
        result.setSortOrder(source.getSortOrder());
        result.setConfigVersion(source.getConfigVersion());
        return result;
    }
}
