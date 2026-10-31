import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  ToolCreatePayload, ToolDetail, ToolInvokeResult, ToolListQuery, ToolPage, ToolSyncResult, ToolUpdatePayload,
} from '@/types/tool'

const ROOT = `${API_PREFIX}/tools`
/** 工具自身超时上限 120 秒，这里留 15 秒余量。 */
const TEST_TIMEOUT_MS = 135_000
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getToolList(query: ToolListQuery): Promise<ToolPage> {
  return get<ToolPage>(ROOT, { params: query })
}

export function getToolDetail(publicId: string): Promise<ToolDetail> {
  return get<ToolDetail>(itemUrl(publicId))
}

export function createTool(payload: ToolCreatePayload): Promise<ToolDetail> {
  return post<ToolDetail>(ROOT, payload)
}

export function updateTool(publicId: string, payload: ToolUpdatePayload): Promise<ToolDetail> {
  return put<ToolDetail>(itemUrl(publicId), payload)
}

export function deleteTool(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 手工测试一个工具。args 是模型给出的入参，整体作为请求体或查询参数。 */
export function testTool(publicId: string, args: Record<string, unknown>): Promise<ToolInvokeResult> {
  return post<ToolInvokeResult>(`${itemUrl(publicId)}/test`, { args }, { timeout: TEST_TIMEOUT_MS })
}

/** 从各来源重新发现工具并同步进目录。 */
export function syncTools(): Promise<ToolSyncResult> {
  return post<ToolSyncResult>(`${ROOT}/sync`)
}
