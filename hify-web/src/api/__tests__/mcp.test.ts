import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import { createMcpServer, deleteMcpServer, getMcpServerList, syncMcpTools, testMcpServer, updateMcpServer } from '../mcp'
import { formFromDetail, mcpPayload, mcpUpdatePayload, parseKeyValues, parseLines } from '@/views/mcp/mcpForm'
import type { McpServerSummary } from '@/types/mcp'

beforeEach(() => vi.clearAllMocks())

describe('MCP API contract', () => {
  it('uses cursor params for the list', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getMcpServerList({ size: 10, cursor: 'cursor' })
    expect(request.get).toHaveBeenCalledWith('/v1/mcp-servers', { params: { size: 10, cursor: 'cursor' } })
  })

  it('uses public IDs for detail, update and deletion', async () => {
    await getMcpServerList({ size: 1 })
    await updateMcpServer('srv-one', { name: '新名字', configVersion: 2 })
    await deleteMcpServer('srv-one')
    expect(request.put).toHaveBeenCalledWith('/v1/mcp-servers/srv-one', expect.objectContaining({ configVersion: 2 }))
    expect(request.del).toHaveBeenCalledWith('/v1/mcp-servers/srv-one')
  })

  it('gives the connection test and the tool sync a longer timeout', async () => {
    await testMcpServer('srv-one')
    await syncMcpTools()
    expect(request.post).toHaveBeenCalledWith('/v1/mcp-servers/srv-one/test', undefined, { timeout: 120_000 })
    expect(request.post).toHaveBeenCalledWith('/v1/mcp-servers/sync', undefined, { timeout: 120_000 })
  })

  it('creates a server with the transport specific fields', async () => {
    await createMcpServer({ name: '文件服务', transport: 'http', url: 'http://127.0.0.1:9000/mcp' })
    expect(request.post).toHaveBeenCalledWith('/v1/mcp-servers',
      expect.objectContaining({ transport: 'http', url: 'http://127.0.0.1:9000/mcp' }))
  })
})

describe('MCP form payload rules', () => {
  it('parses one-per-line text and key=value pairs', () => {
    expect(parseLines(' a \n\n b ')).toEqual(['a', 'b'])
    expect(parseKeyValues('Authorization=Bearer x\nX-Tenant=acme\n坏行')).toEqual({
      Authorization: 'Bearer x', 'X-Tenant': 'acme',
    })
  })

  it('builds a stdio payload from command, args and env', () => {
    const payload = mcpPayload({
      name: ' 文件服务 ', transport: 'stdio', command: ' npx ',
      url: '', argsText: 'server-filesystem\n/tmp', envText: 'TOKEN=abc',
      headersText: '', enabled: true,
    })
    expect(payload).toEqual({
      name: '文件服务', transport: 'stdio', enabled: true,
      command: 'npx', args: ['server-filesystem', '/tmp'], env: { TOKEN: 'abc' },
    })
  })

  it('builds an http payload from url and headers', () => {
    const payload = mcpPayload({
      name: '远端', transport: 'http', command: '', url: ' http://x/mcp ',
      argsText: '', envText: '', headersText: 'Authorization=Bearer k', enabled: false,
    })
    expect(payload).toEqual({
      name: '远端', transport: 'http', enabled: false,
      url: 'http://x/mcp', headers: { Authorization: 'Bearer k' },
    })
  })

  it('carries the config version on update', () => {
    const original: McpServerSummary = {
      publicId: 'srv-one', name: '旧', transport: 'http', url: 'http://x/mcp',
      enabled: true, status: 'online', configVersion: 4,
    }
    const update = mcpUpdatePayload({
      name: '新', transport: 'http', command: '', url: 'http://x/mcp',
      argsText: '', envText: '', headersText: '', enabled: true,
    }, original)
    expect(update.configVersion).toBe(4)
    expect(update.name).toBe('新')
  })

  it('prefills the edit form from the detail', () => {
    const values = formFromDetail({
      publicId: 'srv-one', name: '文件服务', transport: 'http',
      enabled: true, status: 'online', configVersion: 1, url: 'http://x/mcp',
      headers: { Authorization: '******' }, args: ['a', 'b'], env: { TOKEN: 'x' },
    })
    expect(values.url).toBe('http://x/mcp')
    expect(values.headersText).toBe('Authorization=******')
    expect(values.argsText).toBe('a\nb')
    expect(values.envText).toBe('TOKEN=x')
  })
})
