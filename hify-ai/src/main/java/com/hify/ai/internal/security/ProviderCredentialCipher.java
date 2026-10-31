package com.hify.ai.internal.security;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** API Key 的 AES-GCM 加密封套；密钥缺失时拒绝写入凭据，不使用明文回退。 */
public class ProviderCredentialCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ID = "provider-key-v1";
    private static final int FORMAT_VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public ProviderCredentialCipher(String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public Map<String, Object> encrypt(String plaintext) {
        var key = key();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
            cipher.updateAAD(KEY_ID.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", FORMAT_VERSION);
            envelope.put("keyId", KEY_ID);
            envelope.put("nonce", encode(nonce));
            envelope.put("ciphertext", encode(Arrays.copyOf(encrypted, encrypted.length - TAG_BYTES)));
            envelope.put("tag", encode(Arrays.copyOfRange(encrypted, encrypted.length - TAG_BYTES, encrypted.length)));
            return envelope;
        } catch (GeneralSecurityException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "接入点凭据加密失败", exception);
        }
    }

    public String decrypt(Map<String, Object> envelope) {
        var key = key();
        try {
            if (!Integer.valueOf(FORMAT_VERSION).equals(envelope.get("version"))
                    || !KEY_ID.equals(envelope.get("keyId"))) {
                throw new IllegalArgumentException("不支持的凭据封套");
            }
            byte[] nonce = decode(envelope.get("nonce"));
            byte[] tag = decode(envelope.get("tag"));
            byte[] ciphertext = decode(envelope.get("ciphertext"));
            if (nonce.length != NONCE_BYTES || tag.length != TAG_BYTES) {
                throw new IllegalArgumentException("凭据封套长度错误");
            }
            byte[] encrypted = Arrays.copyOf(ciphertext, ciphertext.length + tag.length);
            System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
            cipher.updateAAD(KEY_ID.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "接入点凭据解密失败", exception);
        }
    }

    private SecretKeySpec key() {
        try {
            if (encodedKey == null || encodedKey.isBlank()) {
                throw new IllegalArgumentException("未配置加密密钥");
            }
            byte[] bytes = Base64.getDecoder().decode(encodedKey);
            if (bytes.length != KEY_BYTES) {
                throw new IllegalArgumentException("加密密钥必须为32字节");
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "请配置有效的 HIFY_PROVIDER_ENCRYPTION_KEY（Base64 编码的32字节密钥）", exception);
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private static byte[] decode(Object value) {
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException("缺少凭据封套字段");
        }
        return Base64.getDecoder().decode(string);
    }
}
