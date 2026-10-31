package com.hify.tool.internal.config;

import com.hify.shared.crypto.CredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具凭据加密的装配。
 *
 * <p>密钥从环境变量注入，缺失时<b>拒绝写入凭据</b>，不回退到明文
 * （密钥缺失是部署问题，不是可以糊弄过去的场景）。
 */
@Configuration
public class ToolSecurityConfig {

    /** 封套标识，同时作为 AAD。与接入点侧的 {@code provider-key-v1} 分开，两边密文不通用。 */
    public static final String TOOL_KEY_ID = "tool-credential-v1";

    @Bean
    public CredentialCipher toolCredentialCipher(
            @Value("${hify.tool.encryption-key:${HIFY_TOOL_ENCRYPTION_KEY:}}") String encodedKey) {
        return new CredentialCipher(encodedKey, TOOL_KEY_ID);
    }
}
