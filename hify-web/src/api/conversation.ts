import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  ConversationCreatePayload, ConversationListQuery, ConversationMessage, ConversationPage,
  ConversationSummary, ConversationUpdatePayload,
} from '@/types/conversation'

const ROOT = `${API_PREFIX}/conversations`
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getConversationList(query: ConversationListQuery): Promise<ConversationPage> {
  return get<ConversationPage>(ROOT, { params: query })
}

export function getConversationDetail(publicId: string): Promise<ConversationSummary> {
  return get<ConversationSummary>(itemUrl(publicId))
}

export function createConversation(payload: ConversationCreatePayload): Promise<ConversationSummary> {
  return post<ConversationSummary>(ROOT, payload)
}

export function updateConversation(publicId: string, payload: ConversationUpdatePayload): Promise<ConversationSummary> {
  return put<ConversationSummary>(itemUrl(publicId), payload)
}

export function deleteConversation(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 会话的消息历史，按时间正序。 */
export function getConversationMessages(publicId: string, limit?: number): Promise<ConversationMessage[]> {
  return get<ConversationMessage[]>(`${itemUrl(publicId)}/messages`, {
    params: limit ? { limit } : undefined,
  })
}
