package com.hify.tool.internal.security;

import com.hify.shared.crypto.CredentialCipher;
import com.hify.shared.exception.BizException;
import com.hify.tool.api.ToolDetailResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 请求头凭据的加密、脱敏与合并规则。 */
class ToolHeaderCipherTest {

    private ToolHeaderCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new ToolHeaderCipher(new CredentialCipher(
                Base64.getEncoder().encodeToString(new byte[32]), "tool-credential-v1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "authorization", "X-Api-Key", "x-auth-token", "Cookie", "My-Secret"})
    @DisplayName("名字里含凭据字样的请求头按敏感处理")
    void sensitiveNames(String name) {
        assertThat(cipher.isSensitive(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Type", "Accept", "X-Request-Id"})
    @DisplayName("普通请求头不按敏感处理")
    void plainNames(String name) {
        assertThat(cipher.isSensitive(name)).isFalse();
    }

    @Test
    @DisplayName("敏感头的值加密后不是明文，解密后能还原")
    void encryptRoundTrip() {
        Map<String, Object> stored = cipher.encryptHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer secret-token"));

        assertThat(stored.get("Content-Type")).isEqualTo("application/json");
        assertThat(stored.get("Authorization")).isInstanceOf(Map.class);
        assertThat(stored.get("Authorization").toString()).doesNotContain("secret-token");

        Map<String, String> plain = cipher.decryptHeaders(stored);
        assertThat(plain.get("Authorization")).isEqualTo("Bearer secret-token");
        assertThat(plain.get("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("对外形态里敏感值被占位符替换，明文与密文都不外泄")
    void maskHidesSecrets() {
        Map<String, Object> stored = cipher.encryptHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer secret-token"));

        Map<String, String> masked = cipher.maskHeaders(stored);
        assertThat(masked.get("Authorization")).isEqualTo(ToolDetailResp.MASKED_VALUE);
        assertThat(masked.get("Content-Type")).isEqualTo("application/json");
        assertThat(masked.toString()).doesNotContain("secret-token");
    }

    @Test
    @DisplayName("合并时占位符表示保留原值，明文表示替换，没提交表示删除")
    void mergeSemantics() {
        Map<String, Object> stored = cipher.encryptHeaders(Map.of(
                "Authorization", "Bearer old",
                "X-Trace", "keep-me"));

        Map<String, Object> merged = cipher.mergeHeaders(stored, Map.of(
                "Authorization", ToolDetailResp.MASKED_VALUE,
                "X-Trace", "new-value"));

        assertThat(merged.get("Authorization")).isEqualTo(stored.get("Authorization"));
        assertThat(cipher.decryptHeaders(merged).get("Authorization")).isEqualTo("Bearer old");
        assertThat(cipher.decryptHeaders(merged).get("X-Trace")).isEqualTo("new-value");

        Map<String, Object> removed = cipher.mergeHeaders(stored, Map.of());
        assertThat(removed).isEmpty();
    }

    @Test
    @DisplayName("密钥缺失时拒绝加密，不回退明文")
    void missingKeyFailsClosed() {
        ToolHeaderCipher noKey = new ToolHeaderCipher(new CredentialCipher("", "tool-credential-v1"));
        assertThatThrownBy(() -> noKey.encryptHeaders(Map.of("Authorization", "Bearer x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("加密密钥");
    }
}
