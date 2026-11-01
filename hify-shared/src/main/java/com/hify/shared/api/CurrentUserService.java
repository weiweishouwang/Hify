package com.hify.shared.api;

/**
 * 当前调用者的用户标识。
 *
 * <p><b>跨模块只能显式携带，不能靠 ThreadLocal 传</b>（CLAUDE.md §3.2）——
 * 编排层要把「这条会话是谁的」写进数据库，就必须能跨线程池拿到一个稳定的用户标识。
 *
 * <p><b>一期没有登录接口</b>（CLAUDE.md §五：认证方式还没定）。所以这里的语义是：
 * <ul>
 *   <li>有登录上下文（{@code CurrentUserHolder} 里有人）→ 用那个人</li>
 *   <li>没有 → 落到一个内置的<b>开发用户</b>（不存在就幂等创建）</li>
 * </ul>
 * 将来登录接口接上以后，只要登录拦截器往 {@code CurrentUserHolder} 里塞用户，
 * 这里一行都不用改。
 */
public interface CurrentUserService {

    /**
     * 当前调用者的用户 id。
     *
     * @return 用户 id；不会为 null
     */
    Long currentUserId();
}
