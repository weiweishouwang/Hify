// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { KnowledgeBaseSummary } from '@/types/knowledge'

const api = vi.hoisted(() => ({
  getKnowledgeBaseList: vi.fn(), createKnowledgeBase: vi.fn(), updateKnowledgeBase: vi.fn(),
  deleteKnowledgeBase: vi.fn(), getEmbeddingModels: vi.fn(),
}))
vi.mock('@/api/knowledge', () => api)
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true },
}))
import KnowledgeListView from '../KnowledgeListView.vue'

const row: KnowledgeBaseSummary = {
  publicId: 'kb-one', name: '内部手册', description: '团队内部文档',
  embeddingModel: 'model-one', embeddingDim: 1536, chunkSize: 500, chunkOverlap: 50,
  docCount: 3, status: 'active', configVersion: 2,
}
let wrapper: ReturnType<typeof mount>

let originalMatchMedia: typeof window.matchMedia

beforeEach(() => {
  vi.clearAllMocks()
  // happy-dom 的默认视口宽度落在窄屏断点里，次要列会被隐藏；这里按宽屏渲染
  originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    addListener: vi.fn(), removeListener: vi.fn(),
  }))
  api.getKnowledgeBaseList.mockResolvedValue({ items: [row], hasNext: false })
  api.getEmbeddingModels.mockResolvedValue([
    { publicId: 'model-one', displayName: 'Embedding 小模型', modelName: 'text-embedding-3-small', embeddingDimension: 1536 },
  ])
  api.createKnowledgeBase.mockResolvedValue(row)
  api.updateKnowledgeBase.mockResolvedValue(row)
  api.deleteKnowledgeBase.mockResolvedValue(undefined)
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  window.matchMedia = originalMatchMedia
  document.body.innerHTML = ''
})

async function openPage() {
  wrapper = mount(KnowledgeListView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}
function button(text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('KnowledgeListView real API integration', () => {
  it('loads real rows and shows the chunk parameters', async () => {
    await openPage()
    expect(api.getKnowledgeBaseList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('内部手册')
    expect(wrapper.text()).toContain('500 / 50')
  })

  it('creates a knowledge base with the selected embedding model', async () => {
    await openPage()
    await button('新建知识库').trigger('click')
    await flushPromises()
    expect(api.getEmbeddingModels).toHaveBeenCalled()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '新库', description: '说明', embeddingModel: 'model-one',
      chunkSize: 500, chunkOverlap: 50, status: 'active',
    })
    await flushPromises()
    expect(api.createKnowledgeBase).toHaveBeenCalledWith(expect.objectContaining({
      name: '新库', embeddingModel: 'model-one', chunkSize: 500,
    }))
  })

  it('updates a knowledge base with its config version', async () => {
    await openPage()
    await wrapper.get('[data-testid="edit-kb-one"]').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '改过的名字', description: '', embeddingModel: 'model-one',
      chunkSize: 500, chunkOverlap: 50, status: 'active',
    })
    await flushPromises()
    expect(api.updateKnowledgeBase).toHaveBeenCalledWith('kb-one', expect.objectContaining({
      name: '改过的名字', configVersion: 2,
    }))
  })

  it('deletes a knowledge base through the confirm flow', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-kb-one"]').trigger('click')
    await flushPromises()
    expect(api.deleteKnowledgeBase).toHaveBeenCalledWith('kb-one')
  })
})
