package com.hify.ai.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 整体更新请求；配置版本用于拒绝过期更新，默认保留原凭据。 */
public class ProviderUpdateReq extends ProviderCreateReq {

    /** 调用方读到的配置版本。 */
    @NotNull
    @Positive
    private Long configVersion;

    /** 保留、替换或清除密钥。 */
    @NotNull
    private CredentialActionEnum credentialAction = CredentialActionEnum.KEEP;

    public Long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(Long configVersion) {
        this.configVersion = configVersion;
    }

    public CredentialActionEnum getCredentialAction() {
        return credentialAction;
    }

    public void setCredentialAction(CredentialActionEnum credentialAction) {
        this.credentialAction = credentialAction;
    }

}
