// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationRun, WorkflowRunLog } from '@/types/log'

const api = vi.hoisted(() => ({ getConversationRuns: vi.fn(), getWorkflowRuns: vi.fn() }))
vi.mock('@/api/log', () => api)
import LogListView from '../LogListView.vue'

const conversationRun: ConversationRun = {
  id: 1, sessionPublicId: 'session-one', sessionTitle: '排障问答', agentName: '运维助手',
  status: 'succeeded', promptTokens: 120, completionTokens: 340, toolCallCount: 2,
  latencyMs: 1500, createdAt: '2026-11-02T03:00:00',
}
const workflowRun: WorkflowRunLog = {
  id: 9, definitionPublicId: 'wf-one', definitionName: '日报流程', status: 'failed',
  stepCount: 4, latencyMs: 800, errorMessage: '节点 llm 失败', createdAt: '2026-11-02T04:00:00',
}

let wrapper: ReturnType<typeof mount>
let originalMatchMedia: typeof window.matchMedia

beforeEach(() => {
  vi.clearAllMocks()
  originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: () => {}, removeEventListener: () => {}, addListener: () => {}, removeListener: () => {},
    dispatchEvent: () => false,
  }))
  api.getConversationRuns.mockResolvedValue({ items: [conversationRun], hasNext: false })
  api.getWorkflowRuns.mockResolvedValue({ items: [workflowRun], hasNext: false })
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  window.matchMedia = originalMatchMedia
  document.body.innerHTML = ''
})

async function openPage() {
  // ElSelect 在 happy-dom 下会触发递归更新，桩掉（表格的每页条数选择器用到它）
  wrapper = mount(LogListView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}

describe('LogListView', () => {
  it('lists conversation runs with status and tokens', async () => {
    await openPage()
    expect(api.getConversationRuns).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('排障问答')
    expect(wrapper.text()).toContain('运维助手')
    expect(wrapper.text()).toContain('成功')
    expect(wrapper.text()).toContain('340')
  })

  it('switches to workflow runs on the second tab', async () => {
    await openPage()
    await wrapper.findAll('.el-tabs__item').at(1)!.trigger('click')
    await flushPromises()
    await flushPromises()
    expect(api.getWorkflowRuns).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('日报流程')
    expect(wrapper.text()).toContain('节点 llm 失败')
  })
})
