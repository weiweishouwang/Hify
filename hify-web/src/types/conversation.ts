import type { CursorPage, CursorQuery } from './api'
import type { MessageRole } from './chat'

/** 会话状态：可用 / 已归档。 */
export type ConversationStatus = 'active' | 'archived'

/** 会话摘要。 */
export interface ConversationSummary {
  publicId: string
  title: string
  agentPublicId?: string
  agentName?: string
  messageCount: number
  lastMessageAt?: string
  status: ConversationStatus
  createdAt?: string
  updatedAt?: string
}

export interface ConversationListQuery extends CursorQuery {
  status?: ConversationStatus
  keyword?: string
}

export type ConversationPage = CursorPage<ConversationSummary>

export interface ConversationCreatePayload {
  agentPublicId: string
  title?: string
}

export interface ConversationUpdatePayload {
  title?: string
  status?: ConversationStatus
}

/** 一次工具调用的展示形态。 */
export interface ConversationToolCall {
  id: string
  name: string
  arguments: string
  result?: string
  error?: string
}

/** 一条消息。工具消息的调用明细在 toolCalls 里。 */
export interface ConversationMessage {
  id: number
  role: MessageRole
  content: string
  contentType?: string
  finishReason?: string
  tokenCount?: number
  latencyMs?: number
  createdAt?: string
  toolCalls?: ConversationToolCall[]
}
