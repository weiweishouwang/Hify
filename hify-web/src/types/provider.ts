import type { CursorPage, CursorQuery } from './api'
import type { ModelType } from './model'

export type ProviderType = 'openai' | 'anthropic' | 'ollama'
export type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'
export type CredentialAction = 'KEEP' | 'REPLACE' | 'CLEAR'

export interface ProviderHealth {
  status: HealthStatus
  latencyMs?: number | null
  failCount?: number | null
  lastSuccessAt?: string | null
  lastFailureAt?: string | null
  errorMessage?: string | null
}

export interface ModelConfig {
  publicId: string
  modelName: string
  displayName?: string
  modelType: ModelType
  capabilities?: Record<string, unknown>
  contextWindow?: number | null
  maxOutputTokens?: number | null
  embeddingDimension?: number | null
  defaultParams?: Record<string, unknown>
  extraParams?: Record<string, unknown>
  enabled: boolean
  sortOrder?: number
  configVersion: number
}

export interface ProviderSummary {
  publicId: string
  name: string
  type: ProviderType
  baseUrl: string
  enabled: boolean
  configVersion: number
  credentialsConfigured: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ProviderListItem extends ProviderSummary {
  providerHealth: ProviderHealth
  enabledModelCount: number
}

export interface ProviderConfig extends ProviderSummary {
  authConfig: { anthropicVersion?: string }
  extraParams?: Record<string, unknown>
  modelConfigs: ModelConfig[]
}

export interface ProviderDetail {
  provider: ProviderConfig
  providerHealth: ProviderHealth
}

export interface ProviderListQuery extends CursorQuery {
  type?: ProviderType
  enabled?: boolean
}

export type ProviderPage = CursorPage<ProviderListItem>

export interface ProviderCreatePayload {
  name: string
  type: ProviderType
  baseUrl: string
  authConfig: { apiKey?: string; anthropicVersion?: string }
  extraParams?: Record<string, unknown>
  enabled: boolean
}

export interface ProviderUpdatePayload extends ProviderCreatePayload {
  configVersion: number
  credentialAction: CredentialAction
}

export interface ConnectionTestResult {
  /** 协议连通：最小请求通不通。 */
  connected: boolean
  latencyMs: number
  /** 能力连通：带 tools 定义跑一轮能不能回出 tool_calls；null 表示没测。 */
  capabilitySupported?: boolean | null
  /** 能力连通的说明：为什么没测 / 为什么失败。 */
  capabilityMessage?: string
  /** 能力连通测的是哪个模型。 */
  capabilityModel?: string
  /** 能力连通的耗时（一次真实模型调用，通常是秒级）。 */
  capabilityLatencyMs?: number
}
