import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  ConnectionTestResult, ProviderConfig, ProviderCreatePayload, ProviderDetail,
  ProviderListQuery, ProviderPage, ProviderUpdatePayload,
} from '@/types/provider'

const ROOT = `${API_PREFIX}/providers`
const TEST_TIMEOUT_MS = 75_000
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getProviderList(query: ProviderListQuery): Promise<ProviderPage> {
  return get<ProviderPage>(ROOT, { params: query })
}

export function getProviderDetail(publicId: string): Promise<ProviderDetail> {
  return get<ProviderDetail>(itemUrl(publicId))
}

export function createProvider(payload: ProviderCreatePayload): Promise<ProviderConfig> {
  return post<ProviderConfig>(ROOT, payload)
}

export function updateProvider(publicId: string, payload: ProviderUpdatePayload): Promise<ProviderConfig> {
  return put<ProviderConfig>(itemUrl(publicId), payload)
}

export function deleteProvider(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

export function testConnection(publicId: string): Promise<ConnectionTestResult> {
  return post<ConnectionTestResult>(`${itemUrl(publicId)}/test-connection`, undefined, { timeout: TEST_TIMEOUT_MS })
}
