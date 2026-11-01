package com.hify.shared.config;

import com.hify.shared.crypto.CredentialCipher;
import com.hify.shared.crypto.SensitiveHeaderCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 凭据加密的装配。
 *
 * <p><b>全平台一把钥匙</b>：接入点、工具、MCP 服务的凭据都用同一个
 * {@link CredentialCipher}。分模块各配一把会带来两个问题 ——
 * 容器里出现多个同类型 bean，注入点要到处加 {@code @Qualifier}；
 * 同一份凭据换个模块就解不开。
 *
 * <p>封套的 {@code keyId} 绑在 AAD 上，改它就解不开历史密文 —— 要换密钥得先做迁移。
 *
 * <p>密钥从环境变量注入，缺失时<b>拒绝写入凭据</b>，不回退到明文。
 */
@Configuration(proxyBeanMethods = false)
public class CredentialConfig {

    /** 封套标识，同时作为 AAD。 */
    public static final String CREDENTIAL_KEY_ID = "hify-credential-v1";

    @Bean
    public CredentialCipher credentialCipher(
            @Value("${hify.credential.encryption-key:${HIFY_CREDENTIAL_ENCRYPTION_KEY:${HIFY_TOOL_ENCRYPTION_KEY:}}}") String encodedKey) {
        return new CredentialCipher(encodedKey, CREDENTIAL_KEY_ID);
    }

    /** 敏感请求头的逐值加密；tool 与 mcp 共用同一套规则。 */
    @Bean
    public SensitiveHeaderCipher sensitiveHeaderCipher(CredentialCipher credentialCipher) {
        return new SensitiveHeaderCipher(credentialCipher);
    }
}
