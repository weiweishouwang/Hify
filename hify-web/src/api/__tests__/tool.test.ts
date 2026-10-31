import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import { createTool, deleteTool, getToolDetail, getToolList, syncTools, testTool, updateTool } from '../tool'
import { formFromDetail, headerMap, parseSchema, toolPayload, type ToolForm } from '@/views/tool/toolForm'

beforeEach(() => vi.clearAllMocks())

const form = (): ToolForm => ({
  name: '天气查询',
  toolKey: 'weather_query',
  description: '查天气',
  url: 'https://api.example.com/weather',
  method: 'POST',
  schemaText: '{"type":"object"}',
  headers: [{ name: 'Content-Type', value: 'application/json' }],
  timeoutMs: 30000,
  enabled: true,
})

describe('Tool API contract', () => {
  it('uses cursor params and unwrapped response, not fake page totals', async () => {
    const page = { items: [], hasNext: false }
    request.get.mockResolvedValue(page)
    expect(await getToolList({ size: 10, cursor: 'cursor' })).toBe(page)
    expect(request.get).toHaveBeenCalledWith('/v1/tools', { params: { size: 10, cursor: 'cursor' } })
  })

  it('uses public IDs for details, update, deletion and test', async () => {
    const payload = toolPayload(form())
    await createTool(payload)
    await getToolDetail('tool-one')
    await updateTool('tool-one', { configVersion: 2 })
    await deleteTool('tool-one')
    await testTool('tool-one', { city: '杭州' })
    expect(request.post).toHaveBeenCalledWith('/v1/tools', payload)
    expect(request.get).toHaveBeenCalledWith('/v1/tools/tool-one')
    expect(request.put).toHaveBeenCalledWith('/v1/tools/tool-one', expect.objectContaining({ configVersion: 2 }))
    expect(request.del).toHaveBeenCalledWith('/v1/tools/tool-one')
    expect(request.post).toHaveBeenCalledWith('/v1/tools/tool-one/test', { args: { city: '杭州' } }, { timeout: 135_000 })
  })

  it('triggers the directory sync through the sync endpoint', async () => {
    await syncTools()
    expect(request.post).toHaveBeenCalledWith('/v1/tools/sync')
  })
})

describe('Tool payload rules', () => {
  it('sends sourceType and toolKey only on create', () => {
    const created = toolPayload(form())
    expect(created).toMatchObject({ sourceType: 'http', toolKey: 'weather_query', url: 'https://api.example.com/weather', method: 'POST' })
    expect(created).not.toHaveProperty('configVersion')

    const updated = toolPayload(form(), { configVersion: 4 })
    expect(updated).toMatchObject({ configVersion: 4, enabled: true })
    expect(updated).not.toHaveProperty('sourceType')
    expect(updated).not.toHaveProperty('toolKey')
  })

  it('drops blank header rows and keeps the masked placeholder for unchanged secrets', () => {
    expect(headerMap([{ name: 'Content-Type', value: 'application/json' }, { name: '', value: 'x' }, { name: 'X-Empty', value: '  ' }]))
      .toEqual({ 'Content-Type': 'application/json' })

    const edited = toolPayload({
      ...form(),
      headers: [{ name: 'Authorization', value: '******' }, { name: 'Content-Type', value: 'application/json' }],
    }, { configVersion: 1 })
    expect(edited.headers).toEqual({ Authorization: '******', 'Content-Type': 'application/json' })
  })

  it('treats an invalid or non-object schema as absent', () => {
    expect(parseSchema('')).toBeUndefined()
    expect(parseSchema('not json')).toBeUndefined()
    expect(parseSchema('[1,2]')).toBeUndefined()
    expect(parseSchema('{"type":"object"}')).toEqual({ type: 'object' })
  })

  it('prefills the edit form from the detail and keeps the masked header', () => {
    const values = formFromDetail({
      publicId: 'tool-one', name: '天气查询', sourceType: 'http', toolKey: 'weather_query',
      description: '查天气', enabled: true, configVersion: 2,
      url: 'https://api.example.com/weather', method: 'POST',
      headers: { Authorization: '******', 'Content-Type': 'application/json' },
      schema: { type: 'object' },
    })
    expect(values.toolKey).toBe('weather_query')
    expect(values.headers).toEqual([
      { name: 'Authorization', value: '******' },
      { name: 'Content-Type', value: 'application/json' },
    ])
  })
})
