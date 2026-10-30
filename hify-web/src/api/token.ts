/**
 * 访问令牌的存取。
 *
 * 单独抽成模块，是为了让 api 层（axios 拦截器、SSE 客户端）不必依赖 Pinia
 * —— 否则 api ↔ stores 会互相 import，形成循环依赖。
 *
 * 认证方案本身还没定（共享密码 / OIDC / LDAP，见 CLAUDE.md §五），
 * 但无论哪种方案，最终都是「拿到一个字符串令牌」。
 * 这里只约定存放位置，认证流程换实现时这个文件不用动。
 */
const TOKEN_KEY = 'hify.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
