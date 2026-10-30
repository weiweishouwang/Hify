import { API_PREFIX, get, post } from '@/utils/request'

/**
 * 登录 / 当前用户。
 *
 * 认证方案还没定（共享密码 / OIDC / LDAP，见 CLAUDE.md §五）。
 * 这里按「用户名 + 密码换令牌」实现；若最终选 OIDC，只需把登录页换成跳转，
 * 令牌的存取方式（utils/token.ts）与路由守卫都不用动。
 */

export interface LoginPayload {
  username: string
  password: string
}

export interface CurrentUserInfo {
  id: number
  username: string
  admin: boolean
}

export interface LoginResult extends CurrentUserInfo {
  token: string
}

/** POST /api/v1/auth/login */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return post<LoginResult>(`${API_PREFIX}/auth/login`, payload)
}

/** GET /api/v1/users/me */
export function getCurrentUser(): Promise<CurrentUserInfo> {
  return get<CurrentUserInfo>(`${API_PREFIX}/users/me`)
}
