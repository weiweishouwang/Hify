import { API_BASE_URL, API_PREFIX } from '@/utils/request'
import { getToken } from '@/utils/token'
import { isDone, streamSse, type SseEvent } from './sse'
import type { ToolCall } from '@/types/chat'

/**
 * 流式对话的 SSE 事件契约。
 *
 * 后端 hify-conversation 需要按这个约定发帧（框架已定为 Spring MVC + SSE）：
 *   event: message     data: {"content":"增量文本"}
 *   event: tool_call   data: {"id":"call_1","name":"xxx","arguments":"{...}"}
 *   event: tool_result data: {"id":"call_1","result":"..."}
 *   event: done        data: {"finishReason":"stop"}
 *   event: error       data: {"code":50000,"message":"..."}
 *   data: [DONE]       —— 兜底结束标记，看到即收流
 */
export interface ChatStreamHandlers {
  onText: (delta: string) => void
  onToolCall?: (call: ToolCall) => void
  onToolResult?: (call: ToolCall) => void
  onDone?: (finishReason: string) => void
  onError?: (error: Error) => void
  onOpen?: () => void
  onClose?: () => void
}

/**
 * 发起一次流式对话。
 *
 * 注意这是 POST + body + Bearer 令牌，原生 EventSource 做不到，必须走 fetch。
 */
export function streamChat(
  sessionId: string,
  content: string,
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken()
  return streamSse({
    url: `${API_BASE_URL}${API_PREFIX}/conversations/${encodeURIComponent(sessionId)}/messages`,
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: { content },
    signal,
    onOpen: handlers.onOpen,
    onEvent: (event) => dispatch(event, handlers),
    onError: handlers.onError,
    onClose: handlers.onClose,
  })
}

function dispatch(event: SseEvent, handlers: ChatStreamHandlers): void {
  if (isDone(event)) {
    handlers.onDone?.('stop')
    return
  }

  const payload = parseJson(event.data)
  if (payload === null) {
    return
  }

  switch (event.event) {
    case 'message':
      handlers.onText(String(payload.content ?? ''))
      break
    case 'tool_call':
      handlers.onToolCall?.({
        id: String(payload.id ?? ''),
        name: String(payload.name ?? ''),
        arguments: String(payload.arguments ?? ''),
        status: 'running',
      })
      break
    case 'tool_result':
      handlers.onToolResult?.({
        id: String(payload.id ?? ''),
        name: String(payload.name ?? ''),
        arguments: String(payload.arguments ?? ''),
        result: String(payload.result ?? ''),
        status: payload.error ? 'error' : 'done',
      })
      break
    case 'done':
      handlers.onDone?.(String(payload.finishReason ?? 'stop'))
      break
    case 'error':
      handlers.onError?.(new Error(String(payload.message ?? '对话失败')))
      break
    default:
      break
  }
}

function parseJson(raw: string): Record<string, unknown> | null {
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, unknown>) : null
  } catch {
    // 后端偶尔会发纯文本帧，忽略即可
    return null
  }
}
