// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { AgentDetail, AgentSummary } from '@/types/agent'

const api = vi.hoisted(() => ({
  getAgentList: vi.fn(), getAgentDetail: vi.fn(), getAgentOptions: vi.fn(),
  createAgent: vi.fn(), updateAgent: vi.fn(), deleteAgent: vi.fn(),
}))
vi.mock('@/api/agent', () => api)
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true },
}))
import AgentListView from '../AgentListView.vue'

const row: AgentSummary = {
  publicId: 'agent-one', name: '客服助手', description: '处理售后',
  modelPublicId: 'model-one', modelDisplayName: '通义千问',
  status: 'published', toolCount: 2, knowledgeCount: 1, unavailableCount: 0, configVersion: 3,
}
const detail: AgentDetail = {
  ...row,
  promptTemplate: '你是一个客服', toolIds: ['tool-one'], knowledgeIds: ['kb-one'],
  paramConfig: { temperature: 0.3 }, appType: 'chat',
}
let wrapper: ReturnType<typeof mount>
let originalMatchMedia: typeof window.matchMedia

beforeEach(() => {
  vi.clearAllMocks()
  api.getAgentList.mockResolvedValue({ items: [row], hasNext: false })
  api.getAgentDetail.mockResolvedValue(detail)
  api.getAgentOptions.mockResolvedValue({
    models: [{ publicId: 'model-one', displayName: '通义千问', modelName: 'qwen-plus', providerName: '通义' }],
    tools: [{ publicId: 'tool-one', name: '查订单', sourceType: 'http' }],
    knowledgeBases: [{ publicId: 'kb-one', name: '产品手册', docCount: 3 }],
    truncated: false,
  })
  api.createAgent.mockResolvedValue(detail)
  api.updateAgent.mockResolvedValue(detail)
  api.deleteAgent.mockResolvedValue(undefined)
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  window.matchMedia = originalMatchMedia
  document.body.innerHTML = ''
})

async function openPage() {
  originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    addListener: vi.fn(), removeListener: vi.fn(),
  })) as typeof window.matchMedia
  wrapper = mount(AgentListView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}
function button(text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('AgentListView real API integration', () => {
  it('loads real rows and shows the model and status', async () => {
    await openPage()
    expect(api.getAgentList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('客服助手')
    expect(wrapper.text()).toContain('通义千问')
    expect(wrapper.text()).toContain('已发布')
  })

  it('creates an agent with the real payload', async () => {
    await openPage()
    await button('新增 Agent').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '新 Agent', description: '', modelPublicId: 'model-one', promptTemplate: '',
      toolIds: ['tool-one'], knowledgeIds: [], status: 'draft',
    })
    await flushPromises()
    expect(api.createAgent).toHaveBeenCalledWith(expect.objectContaining({
      name: '新 Agent', modelPublicId: 'model-one', toolIds: ['tool-one'],
    }))
  })

  it('edits an agent and carries the config version from the detail', async () => {
    await openPage()
    await wrapper.get('[data-testid="edit-agent-one"]').trigger('click')
    await flushPromises()
    expect(api.getAgentDetail).toHaveBeenCalledWith('agent-one')
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '改过的名字', description: '', modelPublicId: 'model-one', promptTemplate: '',
      toolIds: [], knowledgeIds: [], status: 'published',
    })
    await flushPromises()
    expect(api.updateAgent).toHaveBeenCalledWith('agent-one', expect.objectContaining({
      name: '改过的名字', configVersion: 3,
    }))
  })

  it('deletes an agent through the confirm flow', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-agent-one"]').trigger('click')
    await flushPromises()
    expect(api.deleteAgent).toHaveBeenCalledWith('agent-one')
  })

  it('flags a row whose bindings are no longer available', async () => {
    api.getAgentList.mockResolvedValue({ items: [{ ...row, unavailableCount: 2 }], hasNext: false })
    await openPage()
    expect(wrapper.text()).toContain('2 项不可用')
  })
})
