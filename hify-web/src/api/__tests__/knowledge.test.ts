import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import {
  createKnowledgeBase, deleteDocument, deleteKnowledgeBase, getDocumentList, getEmbeddingModels,
  getKnowledgeBaseDetail, getKnowledgeBaseList, reindexDocument, searchKnowledge, updateKnowledgeBase,
  uploadDocument,
} from '../knowledge'

beforeEach(() => vi.clearAllMocks())

describe('Knowledge API contract', () => {
  it('uses cursor params and unwrapped response', async () => {
    const page = { items: [], hasNext: false }
    request.get.mockResolvedValue(page)
    expect(await getKnowledgeBaseList({ size: 10, cursor: 'cursor' })).toBe(page)
    expect(request.get).toHaveBeenCalledWith('/v1/knowledge-bases', { params: { size: 10, cursor: 'cursor' } })
  })

  it('scopes documents under the knowledge base', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getDocumentList('kb-one', { size: 10 })
    expect(request.get).toHaveBeenCalledWith('/v1/knowledge-bases/kb-one/documents', { params: { size: 10 } })
  })

  it('uses public IDs for detail, update and deletion', async () => {
    await getKnowledgeBaseDetail('kb-one')
    await updateKnowledgeBase('kb-one', { name: '新名字', configVersion: 2 })
    await deleteKnowledgeBase('kb-one')
    await deleteDocument('doc-one')
    await reindexDocument('doc-one')
    expect(request.get).toHaveBeenCalledWith('/v1/knowledge-bases/kb-one')
    expect(request.put).toHaveBeenCalledWith('/v1/knowledge-bases/kb-one', expect.objectContaining({ configVersion: 2 }))
    expect(request.del).toHaveBeenCalledWith('/v1/knowledge-bases/kb-one')
    expect(request.del).toHaveBeenCalledWith('/v1/documents/doc-one')
    expect(request.post).toHaveBeenCalledWith('/v1/documents/doc-one/reindex')
  })

  it('uploads documents as multipart form data', async () => {
    const file = new File(['内容'], '手册.txt', { type: 'text/plain' })
    await uploadDocument('kb-one', file)
    const [url, body, config] = request.post.mock.calls[0]
    expect(url).toBe('/v1/knowledge-bases/kb-one/documents')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
  })

  it('gives the search a longer timeout than a normal request', async () => {
    await searchKnowledge('kb-one', { query: '手册', topK: 3 })
    expect(request.post).toHaveBeenCalledWith('/v1/knowledge-bases/kb-one/search',
      { query: '手册', topK: 3 }, { timeout: 75_000 })
  })

  it('lists the embedding model options', async () => {
    await getEmbeddingModels()
    expect(request.get).toHaveBeenCalledWith('/v1/knowledge-bases/embedding-models')
  })

  it('creates a knowledge base with the embedding model reference', async () => {
    await createKnowledgeBase({ name: '内部手册', embeddingModel: 'model-one' })
    expect(request.post).toHaveBeenCalledWith('/v1/knowledge-bases',
      expect.objectContaining({ embeddingModel: 'model-one' }))
  })
})
