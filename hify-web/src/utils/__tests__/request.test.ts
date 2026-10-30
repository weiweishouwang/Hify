import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http'

const { errorSpy } = vi.hoisted(() => ({ errorSpy: vi.fn() }))

// element-plus 的 ElMessage 需要 DOM，这里换成假的，只记录调用
vi.mock('element-plus', () => ({
  ElMessage: {
    error: errorSpy,
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

import instance, { API_BASE_URL, API_PREFIX, ApiError, del, get, post, put } from '@/utils/request'

interface TestServer {
  url: string
  close: () => Promise<void>
}

async function startServer(
  handler: (request: IncomingMessage, response: ServerResponse) => void,
): Promise<TestServer> {
  const server: Server = createServer(handler)
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  if (address === null || typeof address === 'string') {
    throw new Error('无法获取测试服务端口')
  }
  return {
    url: `http://127.0.0.1:${address.port}`,
    close: () => new Promise<void>((resolve) => server.close(() => resolve())),
  }
}

/** 起一个按请求返回固定内容的服务，并把 axios 的 baseURL 指过去。 */
async function withServer(
  handler: (request: IncomingMessage, response: ServerResponse) => void,
  body: (url: string) => Promise<void>,
): Promise<void> {
  const server = await startServer(handler)
  instance.defaults.baseURL = server.url
  try {
    await body(server.url)
  } finally {
    await server.close()
  }
}

beforeEach(() => {
  errorSpy.mockClear()
  // node 环境没有 localStorage，token.ts 会被调用到
  vi.stubGlobal('localStorage', {
    getItem: () => null,
    setItem: () => undefined,
    removeItem: () => undefined,
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  // 别的用例会把 baseURL 指向测试服务，用完还原
  instance.defaults.baseURL = API_BASE_URL
})

describe('request 响应拦截器', () => {
  it('code = 0 时自动解包出 data', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify({ code: 0, message: 'ok', data: 'Hify is running' }))
      },
      async () => {
        const result = await get<string>('/v1/health')
        expect(result).toBe('Hify is running')
        expect(errorSpy).not.toHaveBeenCalled()
      },
    )
  })

  it('code = 200 也当成成功并解包', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify({ code: 200, message: 'ok', data: { id: 7 } }))
      },
      async () => {
        const result = await get<{ id: number }>('/v1/thing')
        expect(result).toEqual({ id: 7 })
        expect(errorSpy).not.toHaveBeenCalled()
      },
    )
  })

  it('code 非成功码时提示 message 并 reject', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify({ code: 40400, message: '资源不存在', data: null }))
      },
      async () => {
        await expect(get('/v1/nope')).rejects.toBeInstanceOf(ApiError)
        await expect(get('/v1/nope')).rejects.toMatchObject({
          code: 40400,
          message: '资源不存在',
        })
        expect(errorSpy).toHaveBeenCalledWith('资源不存在')
      },
    )
  })

  it('HTTP 错误状态时展示后端给的 message', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(404, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify({ code: 40400, message: '资源不存在', data: null }))
      },
      async () => {
        await expect(get('/v1/nope')).rejects.toMatchObject({
          code: 40400,
          message: '资源不存在',
          httpStatus: 404,
        })
        expect(errorSpy).toHaveBeenCalledWith('资源不存在')
      },
    )
  })

  it('HTTP 错误状态但响应体不是统一结构时按状态码推导错误码', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(500, { 'Content-Type': 'text/plain' })
        response.end('boom')
      },
      async () => {
        await expect(get('/v1/boom')).rejects.toMatchObject({ code: 50000 })
        expect(errorSpy).toHaveBeenCalledTimes(1)
      },
    )
  })

  it('连不上后端时给出人话提示', async () => {
    const server = await startServer(() => undefined)
    const url = server.url
    await server.close()
    instance.defaults.baseURL = url

    await expect(get('/v1/health')).rejects.toMatchObject({ code: -1 })
    expect(errorSpy).toHaveBeenCalledWith('后端服务暂时不可用，请稍后重试')
  })

  it('响应体不是统一结构时原样返回，不解包也不提示', async () => {
    await withServer(
      (_request, response) => {
        response.writeHead(200, { 'Content-Type': 'application/json' })
        response.end(JSON.stringify([1, 2, 3]))
      },
      async () => {
        const result = await get<number[]>('/v1/list')
        expect(result).toEqual([1, 2, 3])
        expect(errorSpy).not.toHaveBeenCalled()
      },
    )
  })
})

describe('四个请求方法', () => {
  it('baseURL 是 /api，路径带 /v1 时最终地址是 /api/v1/xxx', () => {
    expect(instance.defaults.baseURL).toBe('/api')
    expect(instance.getUri({ url: `${API_PREFIX}/health` })).toBe('/api/v1/health')
    expect(API_BASE_URL).toBe('/api')
  })

  it('get / post / put / del 用对方法并带上 body', async () => {
    const received: Array<{ method: string; url: string; body: string }> = []

    await withServer(
      (request, response) => {
        let body = ''
        request.on('data', (chunk: Buffer) => {
          body += chunk.toString()
        })
        request.on('end', () => {
          received.push({ method: request.method ?? '', url: request.url ?? '', body })
          response.writeHead(200, { 'Content-Type': 'application/json' })
          response.end(JSON.stringify({ code: 0, message: 'ok', data: request.method }))
        })
      },
      async () => {
        expect(await get('/v1/a')).toBe('GET')
        expect(await post('/v1/b', { x: 1 })).toBe('POST')
        expect(await put('/v1/c', { x: 2 })).toBe('PUT')
        expect(await del('/v1/d')).toBe('DELETE')
      },
    )

    expect(received.map((item) => item.method)).toEqual(['GET', 'POST', 'PUT', 'DELETE'])
    expect(received.map((item) => item.url)).toEqual([
      '/v1/a',
      '/v1/b',
      '/v1/c',
      '/v1/d',
    ])
    expect(received[1].body).toBe('{"x":1}')
    expect(received[2].body).toBe('{"x":2}')
  })
})
