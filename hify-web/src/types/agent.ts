import type { CursorPage, CursorQuery } from './api'

/** Agent 的状态：草稿 / 已发布 / 已停用。 */
export type AgentStatus = 'draft' | 'published' | 'disabled'

/** 参数区。只认这三个 —— 上游对不认识的参数是静默丢弃。 */
export interface AgentParamConfig {
  temperature?: number
  maxTokens?: number
  topP?: number
}

/** Agent 摘要：列表项。 */
export interface AgentSummary {
  publicId: string
  name: string
  description?: string
  modelPublicId?: string
  modelDisplayName?: string
  status: AgentStatus
  toolCount: number
  knowledgeCount: number
  /** 当前不可用的引用数（工具 + 知识库）：绑定的东西后来被停用了。 */
  unavailableCount: number
  configVersion: number
  createdAt?: string
  updatedAt?: string
}

/** Agent 详情：含提示词、绑定项与参数区。 */
export interface AgentDetail {
  publicId: string
  name: string
  description?: string
  modelPublicId?: string
  modelDisplayName?: string
  promptTemplate?: string
  toolIds: string[]
  knowledgeIds: string[]
  paramConfig?: AgentParamConfig
  status: AgentStatus
  appType: string
  unavailableCount: number
  configVersion: number
  createdAt?: string
  updatedAt?: string
}

export interface AgentListQuery extends CursorQuery {
  status?: AgentStatus
  keyword?: string
}

export type AgentPage = CursorPage<AgentSummary>

export interface AgentCreatePayload {
  name: string
  description?: string
  modelPublicId: string
  promptTemplate?: string
  toolIds: string[]
  knowledgeIds: string[]
  paramConfig?: AgentParamConfig
  status?: AgentStatus
}

export interface AgentUpdatePayload extends Partial<AgentCreatePayload> {
  configVersion: number
}

export interface AgentModelOption {
  publicId: string
  displayName: string
  modelName: string
  providerName?: string
}

export interface AgentToolOption {
  publicId: string
  name: string
  sourceType: 'builtin' | 'http' | 'mcp'
  description?: string
}

export interface AgentKnowledgeOption {
  publicId: string
  name: string
  description?: string
  docCount?: number
}

/** 新建 / 编辑时可选项。 */
export interface AgentOptions {
  models: AgentModelOption[]
  tools: AgentToolOption[]
  knowledgeBases: AgentKnowledgeOption[]
  /** 某个列表超过单页上限被截断，界面要提示用搜索缩小范围。 */
  truncated: boolean
}
