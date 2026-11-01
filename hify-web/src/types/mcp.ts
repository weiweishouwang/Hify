import type { CursorPage, CursorQuery } from './api'

/** MCP 服务的传输方式。 */
export type McpTransport = 'stdio' | 'http'

/** 连通状态：unknown 还没探测过 / online 连得上 / offline 连不上。 */
export type McpServerStatus = 'unknown' | 'online' | 'offline'

export interface McpServerSummary {
  publicId: string
  name: string
  transport: McpTransport
  command?: string
  args?: string[]
  env?: Record<string, string>
  url?: string
  /** 请求头；敏感值在服务端已脱敏，这里只会看到占位符。 */
  headers?: Record<string, string>
  enabled: boolean
  status: McpServerStatus
  configVersion: number
  lastCheckedAt?: string
  toolCacheAt?: string
  toolCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface McpServerListQuery extends CursorQuery {
  transport?: McpTransport
  status?: McpServerStatus
  enabled?: boolean
  keyword?: string
}

export type McpServerPage = CursorPage<McpServerSummary>

export interface McpServerCreatePayload {
  name: string
  transport: McpTransport
  command?: string
  args?: string[]
  env?: Record<string, string>
  url?: string
  headers?: Record<string, string>
  enabled?: boolean
}

export interface McpServerUpdatePayload {
  name?: string
  transport?: McpTransport
  command?: string
  args?: string[]
  env?: Record<string, string>
  url?: string
  headers?: Record<string, string>
  enabled?: boolean
  configVersion: number
}

export interface McpConnectionTestResult {
  connected: boolean
  serverName?: string
  serverVersion?: string
  protocolVersion?: string
  toolCount: number
  toolNames: string[]
  message?: string
}

export interface McpToolSyncResult {
  servers: Array<{
    publicId: string
    name: string
    connected: boolean
    tools: number
    message?: string
  }>
  discovered: number
  failed: number
}
