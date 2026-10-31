package com.hify.ai.internal.security;

import com.hify.ai.api.CredentialActionEnum;
import com.hify.ai.internal.service.ProviderConfigSupport;
import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.hify.ai.internal.service.ProviderConfigSupport.ANTHROPIC;
import static com.hify.ai.internal.service.ProviderConfigSupport.ANTHROPIC_VERSION;
import static com.hify.ai.internal.service.ProviderConfigSupport.API_KEY;
import static com.hify.ai.internal.service.ProviderConfigSupport.DEFAULT_ANTHROPIC_VERSION;
import static com.hify.ai.internal.service.ProviderConfigSupport.OLLAMA;
import static com.hify.ai.internal.service.ProviderConfigSupport.OPENAI;
import static com.hify.ai.internal.service.ProviderConfigSupport.invalid;

/** 按协议校验鉴权 JSON，更新时显式区分 KEEP / REPLACE / CLEAR。 */
public final class ProviderAuthConfig {
    private ProviderAuthConfig() { }

    public static Map<String, Object> prepare(String type, Map<String, Object> supplied,
                                             Map<String, Object> existing, CredentialActionEnum action,
                                             ProviderCredentialCipher cipher) {
        if (action == null) {
            throw invalid("必须指定凭据更新方式");
        }
        Map<String, Object> input = supplied == null ? Map.of() : supplied;
        Set<String> allowed = ANTHROPIC.equals(type) ? Set.of(API_KEY, ANTHROPIC_VERSION)
                : OPENAI.equals(type) ? Set.of(API_KEY) : Set.of();
        if (!allowed.containsAll(input.keySet())) {
            throw invalid("authConfig 包含当前协议不支持的字段");
        }
        Object raw = input.get(API_KEY);
        if (raw != null && !(raw instanceof String)) {
            throw invalid("新 API Key 必须是字符串，不能提交加密封套");
        }
        String token = raw instanceof String string && !string.isBlank() ? (String) raw : null;
        if (action != CredentialActionEnum.REPLACE && token != null) {
            throw invalid("写入新 API Key 必须选择 REPLACE");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (action == CredentialActionEnum.KEEP && existing != null && existing.get(API_KEY) != null) {
            if (!(existing.get(API_KEY) instanceof Map<?, ?>)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "已保存的接入点凭据格式无效");
            }
            result.put(API_KEY, ProviderConfigSupport.copy(existing).get(API_KEY));
        } else if (action == CredentialActionEnum.REPLACE && token != null) {
            result.put(API_KEY, cipher.encrypt(token));
        }
        if (ANTHROPIC.equals(type)) {
            Object version = input.get(ANTHROPIC_VERSION);
            if (version == null && existing != null && action == CredentialActionEnum.KEEP) {
                version = existing.get(ANTHROPIC_VERSION);
            }
            if (version == null) {
                version = DEFAULT_ANTHROPIC_VERSION;
            }
            if (!(version instanceof String string)) {
                throw invalid("anthropicVersion 必须是日期字符串");
            }
            try {
                LocalDate.parse(string);
            } catch (DateTimeParseException exception) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "anthropicVersion 必须采用 yyyy-MM-dd 日期格式", exception);
            }
            if (!result.containsKey(API_KEY)) {
                throw invalid("Anthropic 接入点必须配置 API Key");
            }
            result.put(ANTHROPIC_VERSION, string);
        }
        if (OLLAMA.equals(type) && !result.isEmpty()) {
            throw invalid("直连 Ollama 不接受鉴权配置");
        }
        return result;
    }
}
