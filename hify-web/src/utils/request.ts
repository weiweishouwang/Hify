import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import type { Result } from '@/types/api'
import { getToken } from './token'

/**
 * 请求基址。
 *
 * axios 实例的 baseURL 与 SSE（fetch）都用它，两边不会漂。
 * 开发期由 vite 代理转发到 localhost:8080，生产期由 nginx 反代到 api:8080，
 * 浏览器侧始终同源，所以这里写相对路径。
 */
export const API_BASE_URL = '/api'

/**
 * 接口版本前缀。
 *
 * baseURL 是 /api，所以完整地址是 /api/v1/xxx —— 调用方拼路径时要带上它。
 */
export const API_PREFIX = '/v1'

/**
 * 成功码。
 *
 * Hify 后端统一用 `code = 0` 表示成功（见 hify-shared 的 `Result.SUCCESS_CODE`），
 * 错误码是 40000 / 40100 / 40400 这种形式，前三位等于 HTTP 状态。
 * 这里同时接受 200 —— 兼容 HTTP 风格的返回体，避免后端哪天换约定时前端全挂。
 */
const SUCCESS_CODE = 0
const HTTP_STYLE_SUCCESS_CODE = 200

/** 普通请求超时。流式请求不走 axios，走 api/sse.ts。 */
const REQUEST_TIMEOUT_MS = 30_000

/**
 * 业务错误。
 *
 * `code` 是后端错误码，`httpStatus` 是 HTTP 状态码 —— 两者都由后端决定，
 * 前端不自己造。
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

const instance: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
})

// ── 请求拦截：带上令牌 ───────────────────────────────────────────────
instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

// ── 响应拦截：拆 Result 外壳，失败一律提示并 reject ──────────────────
instance.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown> | undefined

    // 不是统一响应体（比如直接返回数组、文本）就原样放行，不做解包
    if (!body || typeof body.code !== 'number') {
      return response
    }

    if (body.code === SUCCESS_CODE || body.code === HTTP_STYLE_SUCCESS_CODE) {
      // 自动解包：调用方拿到的直接是 data 字段
      return { ...response, data: body.data }
    }

    const message = body.message || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new ApiError(body.code, message, response.status))
  },
  (error: AxiosError) => {
    const response = error.response
    if (response) {
      // 后端出错时也会返回统一响应体，能取到就展示后端给的 message
      const body = response.data as Result<unknown> | undefined
      const code = body && typeof body.code === 'number' ? body.code : response.status * 100
      const message = body?.message || error.message || '请求失败'
      ElMessage.error(message)
      return Promise.reject(new ApiError(code, message, response.status))
    }
    // 没有响应 = 网络层失败（后端没起来、代理断了）
    ElMessage.error('后端服务暂时不可用，请稍后重试')
    return Promise.reject(new ApiError(-1, error.message || '网络请求失败'))
  },
)

/** GET —— 自动解包出 data。 */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.get(url, config)
  return response.data as T
}

/** POST —— 自动解包出 data。 */
export async function post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.post(url, data, config)
  return response.data as T
}

/** PUT —— 自动解包出 data。 */
export async function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.put(url, data, config)
  return response.data as T
}

/** DELETE —— 自动解包出 data。 */
export async function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.delete(url, config)
  return response.data as T
}

export default instance
