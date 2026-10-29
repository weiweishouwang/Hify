package com.hify.shared.context;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * 当前请求的调用者。
 *
 * <p>不可变值对象。**模块之间必须显式传递这个对象作为方法参数**，
 * 不要依赖 ThreadLocal 跨模块传递——ThreadLocal 只在 Web 入口解析时用一下，
 * 线程池复用会串号（见 CLAUDE.md §3.1 C5）。
 *
 * @param userId   用户标识（对应 user.id），必填
 * @param username 用户名；为空时回退为 {@code userId}
 * @param admin    是否管理员
 * @param authType 认证方式，必填
 */
public record CurrentUser(Long userId, String username, boolean admin, AuthType authType) {

    public CurrentUser {
        Objects.requireNonNull(userId, "userId 不能为空");
        Objects.requireNonNull(authType, "authType 不能为空");
        if (username == null || username.isBlank()) {
            username = String.valueOf(userId);
        }
    }

    /**
     * 网页登录的调用者。
     *
     * @param userId   用户标识（对应 user.id）
     * @param username 用户名，可为空
     * @param admin    是否管理员
     */
    public static CurrentUser ofUser(Long userId, String username, boolean admin) {
        return new CurrentUser(userId, username, admin, AuthType.USER);
    }

    /**
     * API Key 调用。调用者是该 Key 的归属用户，不是 Key 本身。
     *
     * @param userId Key 归属用户的标识
     */
    public static CurrentUser ofApiKey(Long userId) {
        return new CurrentUser(userId, String.valueOf(userId), false, AuthType.API_KEY);
    }

    /** 是否为 API Key 调用。 */
    @JsonIgnore
    public boolean isApiKey() {
        return authType == AuthType.API_KEY;
    }
}
