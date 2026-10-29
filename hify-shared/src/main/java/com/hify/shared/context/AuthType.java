package com.hify.shared.context;

/**
 * 认证方式。
 *
 * <p>区分「谁在调用」，因为两种来源在审计与配额上的语义不同：
 * 网页登录的是真人，API Key 调用的是集成方。
 */
public enum AuthType {

    /** 网页登录，由用户会话解析得到。 */
    USER,

    /** API Key 调用，供内部系统集成 Hify 能力时使用。 */
    API_KEY
}
