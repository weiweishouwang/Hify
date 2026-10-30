import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { ChatMessage, MessageStatus, ToolCall } from '@/types/chat'

let sequence = 0

function nextId(): string {
  sequence += 1
  return `local-${Date.now()}-${sequence}`
}

/**
 * 会话状态：当前会话 + 消息列表。
 *
 * 消息先在本地乐观渲染，流式内容到达后再逐字追加。
 */
export const useConversationStore = defineStore('conversation', () => {
  const sessionId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])

  function setSession(id: string): void {
    sessionId.value = id
    messages.value = []
  }

  function append(message: Omit<ChatMessage, 'id' | 'createdAt'>): ChatMessage {
    const full: ChatMessage = {
      ...message,
      id: nextId(),
      createdAt: new Date().toISOString(),
    }
    messages.value.push(full)
    return full
  }

  /** 追加流式增量。 */
  function appendDelta(id: string, delta: string): void {
    const message = messages.value.find((item) => item.id === id)
    if (!message) {
      return
    }
    message.content += delta
    message.status = 'streaming'
  }

  function setStatus(id: string, status: MessageStatus): void {
    const message = messages.value.find((item) => item.id === id)
    if (message) {
      message.status = status
    }
  }

  function upsertToolCall(id: string, call: ToolCall): void {
    const message = messages.value[messages.value.length - 1]
    if (!message) {
      return
    }
    if (!message.toolCalls) {
      message.toolCalls = []
    }
    const existing = message.toolCalls.find((item) => item.id === id)
    if (existing) {
      Object.assign(existing, call)
    } else {
      message.toolCalls.push(call)
    }
  }

  function clear(): void {
    sessionId.value = null
    messages.value = []
  }

  return { sessionId, messages, setSession, append, appendDelta, setStatus, upsertToolCall, clear }
})
