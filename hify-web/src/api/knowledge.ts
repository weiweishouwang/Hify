import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  DocumentListQuery, DocumentPage, DocumentSummary, EmbeddingModelOption,
  KnowledgeBaseCreatePayload, KnowledgeBaseListQuery, KnowledgeBasePage,
  KnowledgeBaseSummary, KnowledgeBaseUpdatePayload, SearchQueryPayload, SearchResult,
} from '@/types/knowledge'

const ROOT = `${API_PREFIX}/knowledge-bases`
/** 检索含一次模型向量化，比普通请求慢，超时给足。 */
const SEARCH_TIMEOUT_MS = 75_000
const kbUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`
const docUrl = (publicId: string): string => `${API_PREFIX}/documents/${encodeURIComponent(publicId)}`

export function getKnowledgeBaseList(query: KnowledgeBaseListQuery): Promise<KnowledgeBasePage> {
  return get<KnowledgeBasePage>(ROOT, { params: query })
}

export function getKnowledgeBaseDetail(publicId: string): Promise<KnowledgeBaseSummary> {
  return get<KnowledgeBaseSummary>(kbUrl(publicId))
}

export function createKnowledgeBase(payload: KnowledgeBaseCreatePayload): Promise<KnowledgeBaseSummary> {
  return post<KnowledgeBaseSummary>(ROOT, payload)
}

export function updateKnowledgeBase(publicId: string, payload: KnowledgeBaseUpdatePayload): Promise<KnowledgeBaseSummary> {
  return put<KnowledgeBaseSummary>(kbUrl(publicId), payload)
}

export function deleteKnowledgeBase(publicId: string): Promise<void> {
  return del<void>(kbUrl(publicId))
}

/** 可选的 embedding 模型，建库时用。 */
export function getEmbeddingModels(): Promise<EmbeddingModelOption[]> {
  return get<EmbeddingModelOption[]>(`${ROOT}/embedding-models`)
}

export function getDocumentList(kbPublicId: string, query: DocumentListQuery): Promise<DocumentPage> {
  return get<DocumentPage>(`${kbUrl(kbPublicId)}/documents`, { params: query })
}

/**
 * 上传文档。
 *
 * 用 FormData 直传，走的是同一个 axios 实例 —— 鉴权头与错误提示都由请求层统一处理。
 */
export function uploadDocument(kbPublicId: string, file: File): Promise<DocumentSummary> {
  const form = new FormData()
  form.append('file', file)
  return post<DocumentSummary>(`${kbUrl(kbPublicId)}/documents`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120_000,
  })
}

export function deleteDocument(publicId: string): Promise<void> {
  return del<void>(docUrl(publicId))
}

export function reindexDocument(publicId: string): Promise<DocumentSummary> {
  return post<DocumentSummary>(`${docUrl(publicId)}/reindex`)
}

export function searchKnowledge(publicId: string, payload: SearchQueryPayload): Promise<SearchResult[]> {
  return post<SearchResult[]>(`${kbUrl(publicId)}/search`, payload, { timeout: SEARCH_TIMEOUT_MS })
}
