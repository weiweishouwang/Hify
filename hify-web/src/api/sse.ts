/**
 * SSE 客户端。
 *
 * **为什么不能用原生 `EventSource`**：它只支持 GET、不能带请求头和请求体，
 * 而 Hify 的对话接口是 POST + JSON body + Bearer 令牌，三样都用不上。
 * 所以必须用 `fetch` + `ReadableStream` 手动解析 SSE 帧。
 *
 * 帧格式（RFC 6455 之外的 text/event-stream 约定）：
 *   - 帧之间用空行分隔：`\n\n` / `\r\n\r\n` / `\r\r`
 *   - 每帧若干行 `字段: 值`，`data:` 可以有多行，拼接时用 `\n` 连接
 *   - 以 `:` 开头的行是注释，用作心跳，忽略
 *
 * 本文件只管「把字节流变成一个个事件」，业务语义（增量文本 / 工具调用 / 结束）
 * 交给调用方在 onEvent 里解析。
 */

/** 一个 SSE 事件。event 为 undefined 表示服务端没指定事件名（默认 message）。 */
export interface SseEvent {
  event: string | undefined
  data: string
  id: string | undefined
}

export interface SseStreamOptions {
  /** 请求地址。用相对路径即可：开发走 vite 代理，生产走 nginx 反代，都是同源。 */
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  /** 存在时会序列化成 JSON 并带上 Content-Type: application/json。 */
  body?: unknown
  /** 用于中断流。页面卸载时务必 abort，否则连接泄漏。 */
  signal?: AbortSignal
  onOpen?: () => void
  onEvent: (event: SseEvent) => void
  onError?: (error: Error) => void
  onClose?: () => void
}

/** OpenAI 兼容协议用 `[DONE]` 作为流结束标记。 */
export const SSE_DONE_MARKER = '[DONE]'

export function isDone(event: SseEvent): boolean {
  return event.data.trim() === SSE_DONE_MARKER
}

/**
 * 打开一条 SSE 流并逐帧回调。
 *
 * 不抛异常：所有失败都通过 onError 通知，由调用方决定怎么呈现。
 * 返回的 Promise 在流结束（或被 abort）后 resolve。
 */
export async function streamSse(options: SseStreamOptions): Promise<void> {
  const {
    url,
    method = 'POST',
    headers = {},
    body,
    signal,
    onOpen,
    onEvent,
    onError,
    onClose,
  } = options

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers: {
        Accept: 'text/event-stream',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (!isAbortError(error)) {
      onError?.(toError(error))
    }
    onClose?.()
    return
  }

  if (!response.ok) {
    onError?.(new Error(`SSE 连接失败：HTTP ${response.status}`))
    onClose?.()
    return
  }
  if (!response.body) {
    onError?.(new Error('SSE 响应没有可读流'))
    onClose?.()
    return
  }

  onOpen?.()

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      // stream: true —— 多字节字符可能跨 chunk 边界，不能每次单独 decode
      buffer += decoder.decode(value, { stream: true })

      let boundary = findFrameEnd(buffer)
      while (boundary) {
        const raw = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary.length)
        const event = parseFrame(raw)
        if (event) {
          onEvent(event)
        }
        boundary = findFrameEnd(buffer)
      }
    }

    // 收尾：把解码器里可能残留的字节冲出来，再处理没有以空行结尾的最后一帧
    buffer += decoder.decode()
    const tail = parseFrame(buffer)
    if (tail) {
      onEvent(tail)
    }
  } catch (error) {
    if (!isAbortError(error)) {
      onError?.(toError(error))
    }
  } finally {
    reader.cancel().catch(() => {
      // 流已经结束或被 abort，cancel 失败无所谓
    })
    onClose?.()
  }
}

/** 找出缓冲区里最早的一个帧分隔符位置。 */
function findFrameEnd(buffer: string): { index: number; length: number } | null {
  const separators: Array<[string, number]> = [
    ['\r\n\r\n', 4],
    ['\n\n', 2],
    ['\r\r', 2],
  ]
  let found: { index: number; length: number } | null = null
  for (const [token, length] of separators) {
    const index = buffer.indexOf(token)
    if (index !== -1 && (found === null || index < found.index)) {
      found = { index, length }
    }
  }
  return found
}

/** 把一帧的原始文本解析成事件。没有 data 字段的帧（纯心跳）返回 null。 */
function parseFrame(raw: string): SseEvent | null {
  const dataLines: string[] = []
  let event: string | undefined
  let id: string | undefined

  for (const line of raw.split(/\r\n|\n|\r/)) {
    if (line === '' || line.startsWith(':')) {
      // 空行是帧分隔符（已被切掉），`:` 开头是注释/心跳
      continue
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    // 规范规定字段值前的一个空格要去掉
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    switch (field) {
      case 'data':
        dataLines.push(value)
        break
      case 'event':
        event = value
        break
      case 'id':
        id = value
        break
      default:
        // retry 等字段这里用不到
        break
    }
  }

  if (dataLines.length === 0) {
    return null
  }
  return { event, id, data: dataLines.join('\n') }
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

function toError(error: unknown): Error {
  return error instanceof Error ? error : new Error(String(error))
}
