package com.hify.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 从接入点同步上游模型目录的请求。 */
public class ModelSyncReq {

    /** 接入点的对外标识。 */
    @NotBlank(message = "接入点标识不能为空")
    @Size(max = 26, message = "接入点标识过长")
    private String providerPublicId;

    public String getProviderPublicId() {
        return providerPublicId;
    }

    public void setProviderPublicId(String providerPublicId) {
        this.providerPublicId = providerPublicId;
    }
}
