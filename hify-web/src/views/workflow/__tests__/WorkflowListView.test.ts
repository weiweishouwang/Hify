// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { WorkflowSummary } from '@/types/workflow'

const api = vi.hoisted(() => ({
  getWorkflowList: vi.fn(), getWorkflowDetail: vi.fn(), createWorkflow: vi.fn(),
  updateWorkflow: vi.fn(), deleteWorkflow: vi.fn(), runWorkflow: vi.fn(), getWorkflowRuns: vi.fn(),
}))
vi.mock('@/api/workflow', () => api)
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true },
}))
import WorkflowListView from '../WorkflowListView.vue'

const row: WorkflowSummary = {
  publicId: 'wf-one', name: '售后流程', description: '处理售后',
  version: 3, status: 'published', nodeCount: 2,
}
let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  vi.clearAllMocks()
  api.getWorkflowList.mockResolvedValue({ items: [row], hasNext: false })
  api.getWorkflowDetail.mockResolvedValue({ ...row, definition: '{"nodes":[]}' })
  api.createWorkflow.mockResolvedValue({ ...row, definition: '{}' })
  api.updateWorkflow.mockResolvedValue({ ...row, definition: '{}' })
  api.deleteWorkflow.mockResolvedValue(undefined)
  api.runWorkflow.mockResolvedValue({
    status: 'succeeded', output: { result: '好的' }, stepCount: 2, latencyMs: 12, steps: [],
  })
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

async function openPage() {
  wrapper = mount(WorkflowListView, {
    global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } },
  })
  await flushPromises()
}
function button(text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('工作流页真实接口', () => {
  it('加载工作流列表并显示版本与节点数', async () => {
    await openPage()
    expect(api.getWorkflowList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('售后流程')
    expect(wrapper.text()).toContain('已发布')
  })

  it('新建时提交定义文本', async () => {
    await openPage()
    await button('新建工作流').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '新流程', description: '', status: 'draft',
      definition: '{"nodes":[{"id":"n1","type":"output","value":"x"}]}',
    })
    await flushPromises()
    expect(api.createWorkflow).toHaveBeenCalledWith(expect.objectContaining({
      name: '新流程',
      definition: expect.stringContaining('"nodes"'),
    }))
  })

  it('定义不合法时不提交', async () => {
    await openPage()
    await button('新建工作流').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '坏流程', description: '', status: 'draft', definition: '{不是 JSON',
    })
    await flushPromises()
    expect(api.createWorkflow).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('不是合法的 JSON')
  })

  it('编辑时带配置版本', async () => {
    await openPage()
    await wrapper.get('[data-testid="edit-wf-one"]').trigger('click')
    await flushPromises()
    expect(api.getWorkflowDetail).toHaveBeenCalledWith('wf-one')
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '改过的名字', description: '', status: 'published', definition: '{"nodes":[{"id":"n1"}]}',
    })
    await flushPromises()
    expect(api.updateWorkflow).toHaveBeenCalledWith('wf-one', expect.objectContaining({
      name: '改过的名字', version: 3,
    }))
  })

  it('运行工作流并显示输出', async () => {
    await openPage()
    await wrapper.get('[data-testid="run-wf-one"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="run-submit"]').trigger('click')
    await flushPromises()
    expect(api.runWorkflow).toHaveBeenCalledWith('wf-one', expect.objectContaining({
      input: expect.any(String),
    }))
    expect(wrapper.text()).toContain('运行成功')
  })

  it('删除工作流走确认弹窗与真实接口', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-wf-one"]').trigger('click')
    await flushPromises()
    expect(api.deleteWorkflow).toHaveBeenCalledWith('wf-one')
  })
})
