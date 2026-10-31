package com.hify.ai.internal.security;

import com.hify.ai.api.CredentialActionEnum;
import com.hify.shared.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ProviderAuthConfigTest {
    private final ProviderCredentialCipher cipher = new ProviderCredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void anthropicRequiresKeyAndStoresExplicitVersion() {
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("anthropic", Map.of(), null,
                CredentialActionEnum.REPLACE, cipher)).isInstanceOf(BizException.class);
        var config = ProviderAuthConfig.prepare("anthropic", Map.of("apiKey", "secret"), null,
                CredentialActionEnum.REPLACE, cipher);
        assertThat(config).containsEntry("anthropicVersion", "2023-06-01");
        assertThat(config.get("apiKey")).isInstanceOf(Map.class);
        var changed = ProviderAuthConfig.prepare("anthropic", Map.of("anthropicVersion", "2023-01-01"),
                config, CredentialActionEnum.KEEP, cipher);
        assertThat(changed.get("apiKey")).isEqualTo(config.get("apiKey"));
        assertThat(changed).containsEntry("anthropicVersion", "2023-01-01");
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("anthropic", Map.of(), config,
                CredentialActionEnum.CLEAR, cipher)).isInstanceOf(BizException.class);
    }

    @Test
    void localProvidersNeedNoEncryptionKeyAndKeyEnvelopesCannotBeSubmitted() {
        var noKey = new ProviderCredentialCipher("");
        assertThat(ProviderAuthConfig.prepare("ollama", null, null, CredentialActionEnum.REPLACE, noKey)).isEmpty();
        assertThat(ProviderAuthConfig.prepare("openai", Map.of(), null, CredentialActionEnum.REPLACE, noKey)).isEmpty();
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("openai", Map.of("apiKey", Map.of()),
                null, CredentialActionEnum.REPLACE, cipher)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("ollama", Map.of("apiKey", "secret"),
                null, CredentialActionEnum.REPLACE, cipher)).isInstanceOf(BizException.class);
    }

    @Test
    void keepRejectsNewKeyAndInvalidVersionCannotBeSaved() {
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("openai", Map.of("apiKey", "secret"),
                null, CredentialActionEnum.KEEP, cipher)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("anthropic",
                Map.of("apiKey", "secret", "anthropicVersion", "invalid-date"), null,
                CredentialActionEnum.REPLACE, cipher)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> ProviderAuthConfig.prepare("openai", Map.of("unknown", "value"),
                null, CredentialActionEnum.REPLACE, cipher)).isInstanceOf(BizException.class);
    }
}
