import type { CursorPage, CursorQuery } from './api'

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
  modelType: string
  enabled: boolean
  capabilities?: Record<string, unknown>
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
  connected: boolean
  latencyMs: number
}
