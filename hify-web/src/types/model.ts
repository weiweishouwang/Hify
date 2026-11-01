import type { CursorPage, CursorQuery } from './api'

/** 模型用途：chat / embedding。上游目录接口不标类型，由管理台人工认定。 */
export type ModelType = 'chat' | 'embedding'

export interface ModelSummary {
  publicId: string
  providerPublicId?: string
  providerName?: string
  modelName: string
  displayName?: string
  modelType: ModelType
  capabilities?: Record<string, unknown>
  contextWindow?: number | null
  maxOutputTokens?: number | null
  embeddingDimension?: number | null
  enabled: boolean
  sortOrder?: number
  configVersion: number
  createdAt?: string
  updatedAt?: string
}

export interface ModelListQuery extends CursorQuery {
  providerPublicId?: string
  modelType?: ModelType
  enabled?: boolean
  keyword?: string
}

export type ModelPage = CursorPage<ModelSummary>

export interface ModelUpdatePayload {
  displayName?: string
  modelType?: ModelType
  capabilities?: Record<string, unknown>
  contextWindow?: number | null
  maxOutputTokens?: number | null
  embeddingDimension?: number | null
  enabled?: boolean
  sortOrder?: number
  configVersion: number
}

export interface ModelSyncResult {
  discovered: number
  created: number
  updated: number
  disabled: number
}
