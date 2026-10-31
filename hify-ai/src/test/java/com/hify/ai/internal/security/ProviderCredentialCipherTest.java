package com.hify.ai.internal.security;

import com.hify.shared.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class ProviderCredentialCipherTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripUsesFreshNonceAndDoesNotContainPlaintext() {
        var cipher = new ProviderCredentialCipher(key);
        var first = cipher.encrypt("test-provider-secret");
        var second = cipher.encrypt("test-provider-secret");
        assertThat(first).containsKeys("version", "keyId", "nonce", "ciphertext", "tag");
        assertThat(first).isNotEqualTo(second);
        assertThat(first.toString()).doesNotContain("test-provider-secret");
        assertThat(cipher.decrypt(first)).isEqualTo("test-provider-secret");
    }

    @Test
    void missingAndInvalidKeyFailClosedOnlyWhenUsed() {
        var noKey = new ProviderCredentialCipher("");
        assertThatThrownBy(() -> noKey.encrypt("secret")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new ProviderCredentialCipher("invalid").encrypt("secret"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> new ProviderCredentialCipher(
                Base64.getEncoder().encodeToString(new byte[16])).encrypt("secret"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void tamperingAndWrongKeyCannotDecrypt() {
        var cipher = new ProviderCredentialCipher(key);
        var envelope = new HashMap<>(cipher.encrypt("secret"));
        envelope.put("tag", Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> cipher.decrypt(envelope)).isInstanceOf(BizException.class);
        byte[] anotherKey = new byte[32];
        anotherKey[0] = 1;
        assertThatThrownBy(() -> new ProviderCredentialCipher(Base64.getEncoder().encodeToString(anotherKey))
                .decrypt(cipher.encrypt("secret"))).isInstanceOf(BizException.class);
    }
}
