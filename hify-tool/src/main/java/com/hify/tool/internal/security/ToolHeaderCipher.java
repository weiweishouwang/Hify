package com.hify.tool.internal.security;

import com.hify.shared.crypto.CredentialCipher;
import com.hify.tool.api.ToolDetailResp;
import com.hify.tool.internal.service.ToolSupport;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 工具请求头的加密、脱敏与合并。
 *
 * <p><b>敏感头的值落库前加密</b> —— JSON TypeHandler 只做 JSON 转换，不做加密。
 * 落库形态：敏感头的值是一个封套 Map，非敏感头仍是明文字符串。两者类型不同，
 * 因此「这个值是不是密文」不需要额外的约定，看类型就知道。
 *
 * <p>敏感与否按<b>请求头名字</b>判定，不要求用户理解或标注这套机制。
 */
@Component
public class ToolHeaderCipher {

    /** 名字里含这些片段的请求头按敏感处理（不区分大小写）。 */
    private static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "authorization", "token", "secret", "password", "apikey", "api-key", "cookie", "credential");

    private final CredentialCipher cipher;

    public ToolHeaderCipher(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    /** 请求头的名字是否敏感。 */
    public boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }
        String normalized = headerName.toLowerCase().replace("_", "").replace("-", "");
        return SENSITIVE_FRAGMENTS.stream().anyMatch(normalized::contains);
    }

    /**
     * 把明文请求头转成落库形态。
     *
     * @param headers 明文请求头
     * @return 敏感头已加密的请求头
     */
    public Map<String, Object> encryptHeaders(Map<String, String> headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (isSensitive(name)) {
                result.put(name, cipher.encrypt(value));
            } else {
                result.put(name, value);
            }
        });
        return result;
    }

    /**
     * 把落库形态还原成明文，供执行时使用。
     *
     * @param stored 落库的请求头
     * @return 明文请求头
     */
    public Map<String, String> decryptHeaders(Map<String, Object> stored) {
        Map<String, String> result = new LinkedHashMap<>();
        stored.forEach((name, value) -> {
            if (value instanceof Map<?, ?> envelope) {
                result.put(name, cipher.decrypt(castEnvelope(envelope)));
            } else if (value != null) {
                result.put(name, String.valueOf(value));
            }
        });
        return result;
    }

    /**
     * 把落库形态转成对外形态：敏感值替换成占位符，明文与密文都不给。
     *
     * @param stored 落库的请求头
     * @return 脱敏后的请求头
     */
    public Map<String, String> maskHeaders(Map<String, Object> stored) {
        Map<String, String> result = new LinkedHashMap<>();
        stored.forEach((name, value) -> {
            if (value instanceof Map<?, ?> || isSensitive(name)) {
                result.put(name, ToolDetailResp.MASKED_VALUE);
            } else if (value != null) {
                result.put(name, String.valueOf(value));
            }
        });
        return result;
    }

    /**
     * 合并「已落库」与「本次提交」的请求头。
     *
     * <p>语义是<b>完整替换</b>，不是增量：
     * <ul>
     *   <li>提交明文 = 写入新值（敏感头加密）</li>
     *   <li>提交 {@code ******} = 保留原值不动</li>
     *   <li>没提交这个键 = 删掉它</li>
     *   <li>提交空串 = 不设置这个头</li>
     * </ul>
     *
     * @param stored    已落库的请求头
     * @param submitted 本次提交的请求头
     * @return 合并后的落库形态
     */
    public Map<String, Object> mergeHeaders(Map<String, Object> stored, Map<String, String> submitted) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (submitted == null) {
            return result;
        }
        submitted.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null || value.isBlank()) {
                return;
            }
            if (ToolDetailResp.MASKED_VALUE.equals(value)) {
                // 前端把敏感值原样回传，表示「不动它」
                Object existing = stored == null ? null : stored.get(name);
                if (existing != null) {
                    result.put(name, existing);
                }
                return;
            }
            if (isSensitive(name)) {
                result.put(name, cipher.encrypt(value));
            } else {
                result.put(name, value);
            }
        });
        return result;
    }

    /** 校验请求头并把明文转成落库形态，一步到位。 */
    public Map<String, Object> encryptValidatedHeaders(Map<String, String> headers) {
        return encryptHeaders(ToolSupport.headers(headers));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castEnvelope(Map<?, ?> envelope) {
        Map<String, Object> result = new LinkedHashMap<>();
        envelope.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
