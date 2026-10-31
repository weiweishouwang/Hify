import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import { createProvider, deleteProvider, getProviderDetail, getProviderList, testConnection, updateProvider } from '../provider'
import { providerPayload } from '@/views/provider/providerForm'

beforeEach(() => vi.clearAllMocks())

describe('Provider API contract', () => {
  it('uses cursor params and unwrapped response, not fake page totals', async () => {
    const page = { items: [], hasNext: false }
    request.get.mockResolvedValue(page)
    expect(await getProviderList({ size: 10, cursor: 'cursor' })).toBe(page)
    expect(request.get).toHaveBeenCalledWith('/v1/providers', { params: { size: 10, cursor: 'cursor' } })
  })

  it('uses public IDs for details, update, deletion and connection test', async () => {
    const payload = { name: 'Local', type: 'ollama' as const, baseUrl: 'http://localhost:11434', authConfig: {}, enabled: false }
    await createProvider(payload)
    await getProviderDetail('public-id')
    await updateProvider('public-id', { ...payload, configVersion: 2, credentialAction: 'CLEAR' })
    await deleteProvider('public-id')
    await testConnection('public-id')
    expect(request.post).toHaveBeenCalledWith('/v1/providers', payload)
    expect(request.get).toHaveBeenCalledWith('/v1/providers/public-id')
    expect(request.put).toHaveBeenCalledWith('/v1/providers/public-id', expect.objectContaining({ configVersion: 2 }))
    expect(request.del).toHaveBeenCalledWith('/v1/providers/public-id')
    expect(request.post).toHaveBeenCalledWith('/v1/providers/public-id/test-connection', undefined, { timeout: 75_000 })
  })

  it('keeps blank credentials when editing, replaces new keys and clears on protocol changes', () => {
    const form = { name: 'Provider', type: 'openai' as const, baseUrl: 'https://example.com/v1', apiKey: '', anthropicVersion: '2023-06-01', enabled: true }
    const original = { type: 'openai' as const, configVersion: 3, extraParams: { seed: 42 } }
    expect(providerPayload(form, original)).toMatchObject({ credentialAction: 'KEEP', configVersion: 3, authConfig: {}, extraParams: { seed: 42 } })
    expect(providerPayload({ ...form, apiKey: 'new-test-key' }, original)).toMatchObject({ credentialAction: 'REPLACE', authConfig: { apiKey: 'new-test-key' } })
    expect(providerPayload({ ...form, type: 'ollama' }, original)).toMatchObject({ credentialAction: 'CLEAR', authConfig: {} })
    expect(providerPayload({ ...form, type: 'anthropic', apiKey: 'ant-test' })).toMatchObject({ authConfig: { apiKey: 'ant-test', anthropicVersion: '2023-06-01' } })
  })
})
