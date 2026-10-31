import type { CursorPage, CursorQuery } from './api'

/** 工具来源。内置与 MCP 来源的目录行由同步机制托管，用户只手工创建 http 来源。 */
export type ToolSourceType = 'builtin' | 'http' | 'mcp'

/** 一期支持的请求方法。 */
export type ToolMethod = 'GET' | 'POST'

/** 敏感请求头在详情里的占位值；原样回传表示「保留原值不改」。 */
export const MASKED_VALUE = '******'

export interface ToolSummary {
  publicId: string
  name: string
  sourceType: ToolSourceType
  toolKey: string
  description?: string
  enabled: boolean
  configVersion: number
  createdAt?: string
  updatedAt?: string
}

export interface ToolDetail extends ToolSummary {
  schema?: Record<string, unknown>
  url?: string
  method?: ToolMethod
  headers?: Record<string, string>
  timeoutMs?: number
}

export interface ToolListQuery extends CursorQuery {
  sourceType?: ToolSourceType
  enabled?: boolean
  keyword?: string
}

export type ToolPage = CursorPage<ToolSummary>

export interface ToolCreatePayload {
  name: string
  sourceType: ToolSourceType
  toolKey: string
  description?: string
  schema?: Record<string, unknown>
  url: string
  method: ToolMethod
  headers?: Record<string, string>
  timeoutMs?: number
}

export interface ToolUpdatePayload {
  name?: string
  description?: string
  schema?: Record<string, unknown>
  url?: string
  method?: ToolMethod
  headers?: Record<string, string>
  timeoutMs?: number
  enabled?: boolean
  configVersion: number
}

export interface ToolInvokeResult {
  success: boolean
  content?: string
  errorMessage?: string
  metadata?: Record<string, unknown>
  durationMs?: number
}

export interface ToolSourceSyncStat {
  sourceType: string
  discovered: number
  created: number
  updated: number
  disabled: number
}

export interface ToolSyncResult {
  sources: ToolSourceSyncStat[]
  discovered: number
  created: number
  updated: number
  disabled: number
}
