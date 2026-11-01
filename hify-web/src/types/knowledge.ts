import type { CursorPage, CursorQuery } from './api'

/** 文档状态机。 */
export type DocumentStatus = 'uploaded' | 'indexing' | 'ready' | 'failed' | 'deleting'

/** 知识库状态。 */
export type KnowledgeBaseStatus = 'active' | 'disabled'

export interface KnowledgeBaseSummary {
  publicId: string
  name: string
  description?: string
  embeddingModel?: string
  embeddingDim?: number | null
  chunkSize?: number
  chunkOverlap?: number
  docCount?: number
  status: KnowledgeBaseStatus
  configVersion: number
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeBaseListQuery extends CursorQuery {
  status?: KnowledgeBaseStatus
  keyword?: string
}

export type KnowledgeBasePage = CursorPage<KnowledgeBaseSummary>

export interface KnowledgeBaseCreatePayload {
  name: string
  description?: string
  embeddingModel: string
  chunkSize?: number
  chunkOverlap?: number
  status?: KnowledgeBaseStatus
}

export interface KnowledgeBaseUpdatePayload {
  name?: string
  description?: string
  status?: KnowledgeBaseStatus
  configVersion: number
}

/** 可选的 embedding 模型，建库时用。 */
export interface EmbeddingModelOption {
  publicId: string
  displayName?: string
  modelName?: string
  providerName?: string
  embeddingDimension?: number | null
}

export interface DocumentSummary {
  publicId: string
  kbPublicId?: string
  name: string
  fileType: string
  fileSize?: number
  contentHash?: string
  status: DocumentStatus
  chunkCount?: number
  errorMessage?: string
  indexedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface DocumentListQuery extends CursorQuery {
  status?: DocumentStatus
}

export type DocumentPage = CursorPage<DocumentSummary>

export interface SearchQueryPayload {
  query: string
  topK?: number
}

export interface SearchResult {
  chunkId: number
  docId: number
  docName?: string
  chunkIndex: number
  content: string
  score: number
}
