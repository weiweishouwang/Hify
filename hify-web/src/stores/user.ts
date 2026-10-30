import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { clearToken, getToken, setToken } from '@/utils/token'
import type { CurrentUserInfo } from '@/api/modules/auth'

/**
 * 当前用户。
 *
 * 令牌以 localStorage 为准（utils/token.ts 是唯一真源，Pinia 只是它的内存镜像），
 * 刷新页面后状态从 localStorage 恢复。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const profile = ref<CurrentUserInfo | null>(null)

  const isAuthenticated = computed(() => token.value !== null)
  const isAdmin = computed(() => profile.value?.admin === true)
  const displayName = computed(() => profile.value?.username ?? '未登录')

  function login(tokenValue: string): void {
    token.value = tokenValue
    setToken(tokenValue)
  }

  function setProfile(value: CurrentUserInfo | null): void {
    profile.value = value
  }

  function logout(): void {
    token.value = null
    profile.value = null
    clearToken()
  }

  return { token, profile, isAuthenticated, isAdmin, displayName, login, setProfile, logout }
})
