/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 后端 API 前缀，默认 /api/v1（开发走 vite 代理，生产走 nginx 反代）。 */
  readonly VITE_API_BASE_URL?: string
  /** vite 开发服务器的代理目标，默认 http://localhost:8080。 */
  readonly VITE_API_PROXY_TARGET?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
