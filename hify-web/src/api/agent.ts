import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  AgentCreatePayload, AgentDetail, AgentListQuery, AgentOptions, AgentPage, AgentUpdatePayload,
} from '@/types/agent'

const ROOT = `${API_PREFIX}/agents`
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getAgentList(query: AgentListQuery): Promise<AgentPage> {
  return get<AgentPage>(ROOT, { params: query })
}

export function getAgentDetail(publicId: string): Promise<AgentDetail> {
  return get<AgentDetail>(itemUrl(publicId))
}

export function createAgent(payload: AgentCreatePayload): Promise<AgentDetail> {
  return post<AgentDetail>(ROOT, payload)
}

export function updateAgent(publicId: string, payload: AgentUpdatePayload): Promise<AgentDetail> {
  return put<AgentDetail>(itemUrl(publicId), payload)
}

export function deleteAgent(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 新建 / 编辑时可选项：模型、工具、知识库。 */
export function getAgentOptions(): Promise<AgentOptions> {
  return get<AgentOptions>(`${ROOT}/options`)
}
