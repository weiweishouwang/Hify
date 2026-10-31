package com.hify.ai.api;

/** 更新接入点时显式表达凭据变更，空输入不隐式清除已有密钥。 */
public enum CredentialActionEnum {
    KEEP,
    REPLACE,
    CLEAR
}
