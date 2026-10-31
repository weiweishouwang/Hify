// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { ToolDetail, ToolSummary } from '@/types/tool'

const api = vi.hoisted(() => ({
  getToolList: vi.fn(), getToolDetail: vi.fn(), createTool: vi.fn(),
  updateTool: vi.fn(), deleteTool: vi.fn(), testTool: vi.fn(), syncTools: vi.fn(),
}))
vi.mock('@/api/tool', () => api)
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true },
}))
import ToolList from '../ToolList.vue'

const httpRow: ToolSummary = {
  publicId: 'tool-one', name: '回声工具', sourceType: 'http', toolKey: 'echo_tool',
  description: '回显入参', enabled: true, configVersion: 2,
}
const builtinRow: ToolSummary = {
  publicId: 'tool-builtin', name: 'current_time', sourceType: 'builtin', toolKey: 'current_time',
  description: '当前时间', enabled: true, configVersion: 1,
}
const detail: ToolDetail = {
  ...httpRow,
  url: 'https://api.example.com/echo',
  method: 'POST',
  headers: { Authorization: '******', 'Content-Type': 'application/json' },
  schema: { type: 'object' },
  timeoutMs: 30000,
}
let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  vi.clearAllMocks()
  api.getToolList.mockResolvedValue({ items: [httpRow, builtinRow], hasNext: false })
  api.getToolDetail.mockResolvedValue(detail)
  api.createTool.mockResolvedValue(detail)
  api.updateTool.mockResolvedValue(detail)
  api.deleteTool.mockResolvedValue(undefined)
  api.testTool.mockResolvedValue({ success: true, content: '{"ok":true}', durationMs: 12 })
  api.syncTools.mockResolvedValue({ sources: [], discovered: 1, created: 0, updated: 0, disabled: 0 })
})
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks(); document.body.innerHTML = '' })

async function openPage() {
  wrapper = mount(ToolList, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}
function button(text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('ToolList real API integration', () => {
  it('loads real rows and tags the source', async () => {
    await openPage()
    expect(api.getToolList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('回声工具')
    expect(wrapper.text()).toContain('current_time')
    expect(wrapper.text()).toContain('echo_tool')
  })

  it('creates a tool with the real payload', async () => {
    await openPage()
    await button('新增工具').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '回声工具', toolKey: 'echo_tool', description: '回显入参',
      url: 'https://api.example.com/echo', method: 'POST', schemaText: '{"type":"object"}',
      headers: [{ name: 'Content-Type', value: 'application/json' }], timeoutMs: 30000, enabled: true,
    })
    await flushPromises()
    expect(api.createTool).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'http', toolKey: 'echo_tool', url: 'https://api.example.com/echo', method: 'POST',
    }))
  })

  it('edits a tool and keeps the masked secret instead of prefilling it', async () => {
    await openPage()
    await wrapper.get('[data-testid="edit-tool-one"]').trigger('click')
    await flushPromises()
    expect(api.getToolDetail).toHaveBeenCalledWith('tool-one')
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '回声工具', toolKey: 'echo_tool', description: '回显入参',
      url: 'https://api.example.com/echo', method: 'POST', schemaText: '',
      headers: [{ name: 'Authorization', value: '******' }], timeoutMs: 30000, enabled: true,
    })
    await flushPromises()
    expect(api.updateTool).toHaveBeenCalledWith('tool-one', expect.objectContaining({
      configVersion: 2, headers: { Authorization: '******' },
    }))
  })

  it('runs a tool test with the entered arguments and shows the result', async () => {
    await openPage()
    await wrapper.get('[data-testid="test-tool-one"]').trigger('click')
    await flushPromises()
    const dialog = wrapper.find('.el-dialog').exists() ? wrapper.find('.el-dialog') : wrapper.findComponent({ name: 'ElDialog' })
    expect(dialog.exists()).toBe(true)
    const textarea = dialog.find('textarea')
    await textarea.setValue('{"city":"杭州"}')
    await flushPromises()
    const runButton = dialog.findAll('button').find(item => item.text().includes('执行'))!
    await runButton.trigger('click')
    await flushPromises()
    expect(api.testTool).toHaveBeenCalledWith('tool-one', { city: '杭州' })
    expect(wrapper.text()).toContain('执行成功')
  })

  it('deletes a tool through the confirm flow', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-tool-one"]').trigger('click')
    await flushPromises()
    expect(api.deleteTool).toHaveBeenCalledWith('tool-one')
  })

  it('hides edit and delete for builtin tools whose rows the sync owns', async () => {
    await openPage()
    expect(wrapper.find('[data-testid="edit-tool-builtin"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="delete-tool-builtin"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="test-tool-builtin"]').exists()).toBe(true)
  })

  it('triggers the directory sync from the toolbar', async () => {
    await openPage()
    await button('同步目录').trigger('click')
    await flushPromises()
    expect(api.syncTools).toHaveBeenCalled()
  })
})
