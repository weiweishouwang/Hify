package com.hify.ai.internal.config;

import com.hify.ai.internal.security.ProviderCredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 凭据密钥由部署环境提供，不写入数据库，也不为缺失密钥生成默认值。 */
@Configuration(proxyBeanMethods = false)
public class ProviderSecurityConfig {
    @Bean
    public ProviderCredentialCipher providerCredentialCipher(
            @Value("${hify.ai.provider-encryption-key:${HIFY_PROVIDER_ENCRYPTION_KEY:}}") String key) {
        return new ProviderCredentialCipher(key);
    }
}
