import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import type { Result } from '@/types/api'
import { getToken } from './token'

/** 后端约定：code = 0 表示成功。 */
export const SUCCESS_CODE = 0

/**
 * 业务错误。`code` 是后端错误码（见 hify-shared 的 ErrorCode），
 * `httpStatus` 是 HTTP 状态码，两者都不是前端自己造的。
 */
export class ApiError extends Error {
  readonly code: number
  readonly httpStatus: number

  constructor(code: number, message: string, httpStatus = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

/**
 * API 前缀。
 *
 * 开发环境走 vite 代理（浏览器看到 /api/v1），生产环境走 nginx 反代（同样是 /api/v1），
 * 两种环境浏览器侧都是同源，所以这里不写绝对地址，前端也不用处理 CORS。
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** 普通请求超时。流式请求不走 axios，走 sse.ts。 */
const REQUEST_TIMEOUT_MS = 30_000

const http: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown> | undefined
    // 只有统一响应体才校验 code；非统一结构（如 actuator 探针）原样放行
    if (body && typeof body.code === 'number' && body.code !== SUCCESS_CODE) {
      throw new ApiError(body.code, body.message || '请求失败', response.status)
    }
    return response
  },
  (error: AxiosError) => {
    if (error.response) {
      const status = error.response.status
      const body = error.response.data as Result<unknown> | undefined
      // 后端错误码的前三位就是 HTTP 状态（40000 ↔ 400），非统一响应体时按此推导
      const code = body && typeof body.code === 'number' ? body.code : status * 100
      const message = body?.message || error.message || '请求失败'
      return Promise.reject(new ApiError(code, message, status))
    }
    // 没有响应 = 网络层失败（后端没起来、代理断了、被浏览器拦截）
    return Promise.reject(new ApiError(-1, error.message || '网络请求失败'))
  },
)

/**
 * 统一出口：拆掉 Result 外壳，只把 data 交给调用方。
 * 失败时抛 ApiError。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<Result<T>>(config)
  return response.data.data
}

export default http
