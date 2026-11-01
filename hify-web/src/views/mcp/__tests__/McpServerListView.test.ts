// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { McpServerSummary } from '@/types/mcp'

const api = vi.hoisted(() => ({
  getMcpServerList: vi.fn(), createMcpServer: vi.fn(), updateMcpServer: vi.fn(),
  deleteMcpServer: vi.fn(), testMcpServer: vi.fn(), syncMcpTools: vi.fn(),
}))
vi.mock('@/api/mcp', () => api)
vi.mock('@/composables/useConfirm', () => ({
  useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true },
}))
import McpServerListView from '../McpServerListView.vue'

const row: McpServerSummary = {
  publicId: 'srv-one', name: '文件服务', transport: 'http', url: 'http://127.0.0.1:9000/mcp',
  enabled: true, status: 'online', configVersion: 3, toolCount: 2,
}
let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  vi.clearAllMocks()
  api.getMcpServerList.mockResolvedValue({ items: [row], hasNext: false })
  api.createMcpServer.mockResolvedValue(row)
  api.updateMcpServer.mockResolvedValue(row)
  api.deleteMcpServer.mockResolvedValue(undefined)
  api.testMcpServer.mockResolvedValue({ connected: true, toolCount: 2, toolNames: ['read', 'write'], protocolVersion: '2025-06-18' })
  api.syncMcpTools.mockResolvedValue({ servers: [], discovered: 2, failed: 0 })
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  window.matchMedia = originalMatchMedia
  document.body.innerHTML = ''
})

let originalMatchMedia: typeof window.matchMedia

async function openPage() {
  originalMatchMedia = window.matchMedia
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false, media: query, onchange: null,
    addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
    addListener: vi.fn(), removeListener: vi.fn(),
  })) as typeof window.matchMedia
  wrapper = mount(McpServerListView, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}
function button(text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('McpServerListView real API integration', () => {
  it('loads real rows and shows the transport and status', async () => {
    await openPage()
    expect(api.getMcpServerList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('文件服务')
    expect(wrapper.text()).toContain('HTTP')
    expect(wrapper.text()).toContain('在线')
  })

  it('creates a server with the real payload', async () => {
    await openPage()
    await button('新增服务').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '新服务', transport: 'http', command: '', url: 'http://x/mcp',
      argsText: '', envText: '', headersText: '', enabled: true,
    })
    await flushPromises()
    expect(api.createMcpServer).toHaveBeenCalledWith(expect.objectContaining({
      name: '新服务', transport: 'http', url: 'http://x/mcp',
    }))
  })

  it('edits a server and carries the config version', async () => {
    await openPage()
    await wrapper.get('[data-testid="edit-srv-one"]').trigger('click')
    await flushPromises()
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', {
      name: '改过的名字', transport: 'http', command: '', url: 'http://x/mcp',
      argsText: '', envText: '', headersText: '', enabled: true,
    })
    await flushPromises()
    expect(api.updateMcpServer).toHaveBeenCalledWith('srv-one', expect.objectContaining({
      name: '改过的名字', configVersion: 3,
    }))
  })

  it('runs a connection test from the row action', async () => {
    await openPage()
    await wrapper.get('[data-testid="test-srv-one"]').trigger('click')
    await flushPromises()
    expect(api.testMcpServer).toHaveBeenCalledWith('srv-one')
  })

  it('deletes a server through the confirm flow', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-srv-one"]').trigger('click')
    await flushPromises()
    expect(api.deleteMcpServer).toHaveBeenCalledWith('srv-one')
  })

  it('syncs the tools from the toolbar', async () => {
    await openPage()
    await button('同步工具').trigger('click')
    await flushPromises()
    expect(api.syncMcpTools).toHaveBeenCalled()
  })
})
