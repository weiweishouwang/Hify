package com.hify.shared.crypto;

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

/**
 * 凭据的 AES-GCM 加密封套。
 *
 * <p><b>只有加密，没有 JSON 转换</b> —— MyBatis-Plus 的 {@code JacksonTypeHandler} 负责
 * JSON 转换，不负责加密（Provider 阶段踩过：JSON 列里存明文密钥）。凭据要落库，
 * 就得自己走一遍加密。
 *
 * <p>封套是一个 JSON 可序列化的 Map：
 * <pre>{@code
 * { "version": 1, "keyId": "tool-credential-v1",
 *   "nonce": "<base64>", "ciphertext": "<base64>", "tag": "<base64>" }
 * }</pre>
 * AAD 绑定 {@code keyId}，所以不同 keyId 的封套互相解不开 —— 即使共用同一把密钥。
 *
 * <p><b>keyId 由调用方指定，不写死</b>：历史数据里的封套带着当时的 keyId，
 * 改掉它就解不开旧密文了。新接入方用自己的 keyId 即可。
 *
 * <p>密钥缺失或长度不对时<b>拒绝加密</b>（fail closed），不回退到明文。
 */
public class CredentialCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int FORMAT_VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;

    private final String encodedKey;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param encodedKey Base64 编码的 32 字节密钥
     * @param keyId      封套标识，同时作为 AAD；不同用途用不同值
     */
    public CredentialCipher(String encodedKey, String keyId) {
        this.encodedKey = encodedKey;
        this.keyId = keyId;
    }

    /**
     * 加密一段明文。
     *
     * @param plaintext 明文
     * @return 可直接放进 JSON 列的封套
     * @throws BizException 密钥无效或加密失败时抛出
     */
    public Map<String, Object> encrypt(String plaintext) {
        if (plaintext == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "待加密的凭据不能为空");
        }
        var key = key();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
            cipher.updateAAD(aad());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", FORMAT_VERSION);
            envelope.put("keyId", keyId);
            envelope.put("nonce", encode(nonce));
            envelope.put("ciphertext", encode(Arrays.copyOf(encrypted, encrypted.length - TAG_BYTES)));
            envelope.put("tag", encode(Arrays.copyOfRange(encrypted, encrypted.length - TAG_BYTES, encrypted.length)));
            return envelope;
        } catch (GeneralSecurityException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "凭据加密失败", exception);
        }
    }

    /**
     * 解开一个封套。
     *
     * @param envelope {@link #encrypt(String)} 产出的封套
     * @return 明文
     * @throws BizException 封套格式不对、密钥无效或解密失败时抛出
     */
    public String decrypt(Map<String, Object> envelope) {
        var key = key();
        try {
            if (envelope == null
                    || !Integer.valueOf(FORMAT_VERSION).equals(envelope.get("version"))
                    || !keyId.equals(envelope.get("keyId"))) {
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
            cipher.updateAAD(aad());
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "凭据解密失败", exception);
        }
    }

    /** 当前封套的 keyId。 */
    public String keyId() {
        return keyId;
    }

    private byte[] aad() {
        return keyId.getBytes(StandardCharsets.UTF_8);
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
                    "请配置有效的凭据加密密钥（Base64 编码的32字节密钥）", exception);
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
