import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  McpConnectionTestResult, McpServerCreatePayload, McpServerListQuery, McpServerPage,
  McpServerSummary, McpServerUpdatePayload, McpToolSyncResult,
} from '@/types/mcp'

const ROOT = `${API_PREFIX}/mcp-servers`
/** 连接测试要 initialize 并拉一遍工具列表，比普通请求慢。 */
const TEST_TIMEOUT_MS = 120_000
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getMcpServerList(query: McpServerListQuery): Promise<McpServerPage> {
  return get<McpServerPage>(ROOT, { params: query })
}

export function getMcpServerDetail(publicId: string): Promise<McpServerSummary> {
  return get<McpServerSummary>(itemUrl(publicId))
}

export function createMcpServer(payload: McpServerCreatePayload): Promise<McpServerSummary> {
  return post<McpServerSummary>(ROOT, payload)
}

export function updateMcpServer(publicId: string, payload: McpServerUpdatePayload): Promise<McpServerSummary> {
  return put<McpServerSummary>(itemUrl(publicId), payload)
}

export function deleteMcpServer(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 连上去试一次：initialize + tools/list。 */
export function testMcpServer(publicId: string): Promise<McpConnectionTestResult> {
  return post<McpConnectionTestResult>(`${itemUrl(publicId)}/test`, undefined, { timeout: TEST_TIMEOUT_MS })
}

/** 刷新所有启用服务的工具缓存。 */
export function syncMcpTools(): Promise<McpToolSyncResult> {
  return post<McpToolSyncResult>(`${ROOT}/sync`, undefined, { timeout: TEST_TIMEOUT_MS })
}
