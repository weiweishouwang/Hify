import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import { deleteModel, getModelDetail, getModelList, syncModels, updateModel } from '../model'

beforeEach(() => vi.clearAllMocks())

describe('Model API contract', () => {
  it('uses cursor params for the list', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getModelList({ size: 10, cursor: 'cursor' })
    expect(request.get).toHaveBeenCalledWith('/v1/models', { params: { size: 10, cursor: 'cursor' } })
  })

  it('updates a model with its config version', async () => {
    await updateModel('model-one', { modelType: 'embedding', embeddingDimension: 1536, configVersion: 3 })
    expect(request.put).toHaveBeenCalledWith('/v1/models/model-one',
      expect.objectContaining({ modelType: 'embedding', embeddingDimension: 1536, configVersion: 3 }))
  })

  it('reads and deletes by public ID', async () => {
    await getModelDetail('model-one')
    await deleteModel('model-one')
    expect(request.get).toHaveBeenCalledWith('/v1/models/model-one')
    expect(request.del).toHaveBeenCalledWith('/v1/models/model-one')
  })

  it('syncs the upstream catalog for a provider', async () => {
    await syncModels('provider-one')
    expect(request.post).toHaveBeenCalledWith('/v1/models/sync',
      { providerPublicId: 'provider-one' }, { timeout: 60_000 })
  })
})
