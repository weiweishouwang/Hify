package com.hify.shared.context;

import com.hify.shared.exception.BizException;
import com.hify.shared.exception.ErrorCode;

/**
 * 当前用户的线程上下文。
 *
 * <p>**只允许在 Web 入口（Filter / Interceptor）解析并设置，模块之间必须显式传递
 * {@link CurrentUser} 参数。** 原因：线程池会复用线程，ThreadLocal 里残留的值会被
 * 下一次请求读到——这在流式对话与异步工具调用里尤其容易串号
 * （见 CLAUDE.md §3.1 C5）。
 *
 * <p>Web 层必须在 {@code finally} 里调用 {@link #clear()}。
 */
public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    /** 设置当前用户。 */
    public static void set(CurrentUser currentUser) {
        HOLDER.set(currentUser);
    }

    /** 取当前用户；未登录时返回 null。 */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    /**
     * 取当前用户；未登录时抛 {@link ErrorCode#UNAUTHORIZED}。
     *
     * @return 当前用户
     * @throws BizException 未登录时抛出
     */
    public static CurrentUser require() {
        CurrentUser currentUser = HOLDER.get();
        if (currentUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return currentUser;
    }

    /**
     * 取当前用户 id；未登录时返回 null（供自动填充等"有则填、无则跳过"的场景使用）。
     */
    public static Long currentUserId() {
        CurrentUser currentUser = HOLDER.get();
        return currentUser == null ? null : currentUser.userId();
    }

    /** 清理当前用户。Web 层必须在 finally 中调用。 */
    public static void clear() {
        HOLDER.remove();
    }
}
