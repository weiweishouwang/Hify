import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type { ModelListQuery, ModelPage, ModelSummary, ModelSyncResult, ModelUpdatePayload } from '@/types/model'

const ROOT = `${API_PREFIX}/models`
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getModelList(query: ModelListQuery): Promise<ModelPage> {
  return get<ModelPage>(ROOT, { params: query })
}

export function getModelDetail(publicId: string): Promise<ModelSummary> {
  return get<ModelSummary>(itemUrl(publicId))
}

export function updateModel(publicId: string, payload: ModelUpdatePayload): Promise<ModelSummary> {
  return put<ModelSummary>(itemUrl(publicId), payload)
}

export function deleteModel(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 从接入点同步上游模型目录；同步会拉一次上游接口，耗时较长。 */
export function syncModels(providerPublicId: string): Promise<ModelSyncResult> {
  return post<ModelSyncResult>(`${ROOT}/sync`, { providerPublicId }, { timeout: 60_000 })
}
