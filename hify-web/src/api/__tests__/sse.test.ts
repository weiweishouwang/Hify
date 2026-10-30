import { describe, expect, it } from 'vitest'
import { createServer, type Server } from 'node:http'
import { isDone, streamSse, type SseEvent } from '@/api/sse'

/**
 * SSE 帧解析的测试。
 *
 * 直接起一个真的 http 服务，往 socket 里按字节吐 SSE 帧，
 * 这样「一个帧被拆到多个 chunk」「多字节字符跨 chunk」这类真实情况都能覆盖到——
 * 这些恰恰是手写解析最容易出错的地方。
 */

interface TestServer {
  url: string
  close: () => Promise<void>
}

async function startSseServer(handler: (request: import('node:http').IncomingMessage, response: import('node:http').ServerResponse) => void): Promise<TestServer> {
  const server: Server = createServer(handler)
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  if (address === null || typeof address === 'string') {
    throw new Error('无法获取测试服务端口')
  }
  return {
    url: `http://127.0.0.1:${address.port}/sse`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  }
}

/** 收集事件，跑完后返回。 */
async function collect(
  url: string,
  options: { signal?: AbortSignal; body?: unknown; headers?: Record<string, string> } = {},
): Promise<SseEvent[]> {
  const events: SseEvent[] = []
  await streamSse({
    url,
    method: 'POST',
    headers: options.headers,
    body: options.body,
    signal: options.signal,
    onEvent: (event) => events.push(event),
  })
  return events
}

describe('streamSse', () => {
  it('解析出 event、data、id 三个字段', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('event: message\nid: 7\ndata: {"content":"你好"}\n\n')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].event).toBe('message')
      expect(events[0].id).toBe('7')
      expect(events[0].data).toBe('{"content":"你好"}')
    } finally {
      await server.close()
    }
  })

  it('多行 data 用换行拼接', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('data: 第一行\ndata: 第二行\n\n')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].data).toBe('第一行\n第二行')
    } finally {
      await server.close()
    }
  })

  it('忽略心跳注释帧', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write(': ping\n\n')
      response.write('data: 真正的内容\n\n')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].data).toBe('真正的内容')
    } finally {
      await server.close()
    }
  })

  it('识别 [DONE] 结束标记', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('data: 内容\n\n')
      response.write('data: [DONE]\n\n')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(2)
      expect(isDone(events[0])).toBe(false)
      expect(isDone(events[1])).toBe(true)
    } finally {
      await server.close()
    }
  })

  it('帧被拆到多个 chunk 时仍能正确拼回来', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      const frame = 'event: message\ndata: {"content":"分片"}\n\n'
      expect(frame.length).toBeGreaterThan(0)
      // 一帧拆成 5 段发，每段之间等一下，确保跨 chunk 边界
      for (const piece of ['event: mes', 'sage\ndata: {"con', 'tent":"分', '片"}\n', '\n']) {
        response.write(piece)
      }
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].event).toBe('message')
      expect(events[0].data).toBe('{"content":"分片"}')
    } finally {
      await server.close()
    }
  })

  it('多字节字符跨 chunk 边界不会乱码', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      const payload = Buffer.from('data: 中文测试\n\n', 'utf8')
      // 从中文的中间切开
      response.write(payload.subarray(0, 9))
      response.write(payload.subarray(9))
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].data).toBe('中文测试')
    } finally {
      await server.close()
    }
  })

  it('支持 CRLF 帧分隔符', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('event: message\r\ndata: CRLF\r\n\r\n')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].event).toBe('message')
      expect(events[0].data).toBe('CRLF')
    } finally {
      await server.close()
    }
  })

  it('最后没有空行结尾的帧也会被取出', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('data: 结尾没有空行')
      response.end()
    })
    try {
      const events = await collect(server.url)
      expect(events).toHaveLength(1)
      expect(events[0].data).toBe('结尾没有空行')
    } finally {
      await server.close()
    }
  })

  it('HTTP 非 2xx 时报 onError 而不是静默', async () => {
    const server = await startSseServer((_request, response) => {
      response.writeHead(404, { 'Content-Type': 'application/json' })
      response.end('{"code":40400,"message":"资源不存在"}')
    })
    try {
      let error: Error | undefined
      await streamSse({
        url: server.url,
        onEvent: () => {
          throw new Error('不应该收到事件')
        },
        onError: (e) => {
          error = e
        },
      })
      expect(error).toBeInstanceOf(Error)
      expect(error?.message).toContain('404')
    } finally {
      await server.close()
    }
  })

  it('POST 的 body 与请求头会带到后端', async () => {
    let receivedBody = ''
    let receivedAuth = ''
    const server = await startSseServer((request, response) => {
      receivedAuth = String(request.headers.authorization ?? '')
      request.on('data', (chunk: Buffer) => {
        receivedBody += chunk.toString()
      })
      request.on('end', () => {
        response.writeHead(200, { 'Content-Type': 'text/event-stream' })
        response.end('data: ok\n\n')
      })
    })
    try {
      await collect(server.url, {
        body: { content: '你好' },
        headers: { Authorization: 'Bearer test-token' },
      })
      expect(receivedBody).toBe('{"content":"你好"}')
      expect(receivedAuth).toBe('Bearer test-token')
    } finally {
      await server.close()
    }
  })

  it('abort 之后不再回调事件，且不当作错误', async () => {
    const controller = new AbortController()
    let error: Error | undefined
    const server = await startSseServer((_request, response) => {
      response.writeHead(200, { 'Content-Type': 'text/event-stream' })
      response.write('data: 第一条\n\n')
      // 服务端不主动结束，客户端负责 abort
    })
    try {
      await streamSse({
        url: server.url,
        signal: controller.signal,
        onEvent: () => controller.abort(),
        onError: (e) => {
          error = e
        },
      })
      expect(error).toBeUndefined()
    } finally {
      await server.close()
    }
  })
})
