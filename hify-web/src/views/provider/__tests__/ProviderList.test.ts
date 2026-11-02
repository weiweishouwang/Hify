// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import type { ProviderConfig, ProviderListItem } from '@/types/provider'

const api = vi.hoisted(() => ({ getProviderList: vi.fn(), getProviderDetail: vi.fn(), createProvider: vi.fn(), updateProvider: vi.fn(), deleteProvider: vi.fn(), testConnection: vi.fn() }))
vi.mock('@/api/provider', () => api)
vi.mock('@/composables/useConfirm', () => ({ useConfirm: (_message: string, action: () => Promise<unknown>) => async () => { await action(); return true } }))
import ProviderList from '../ProviderList.vue'

const row: ProviderListItem = { publicId: 'provider-one', name: 'Real provider', type: 'openai', baseUrl: 'https://example.com/v1', enabled: true, configVersion: 3, credentialsConfigured: true, enabledModelCount: 1, providerHealth: { status: 'UP', latencyMs: 0 } }
const config: ProviderConfig = { ...row, authConfig: {}, extraParams: { seed: 1 }, modelConfigs: [{ publicId: 'model-one', displayName: 'Model One', modelName: 'upstream-model', modelType: 'chat', enabled: true, configVersion: 1 }] }
let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  vi.clearAllMocks()
  api.getProviderList.mockResolvedValue({ items: [row], hasNext: false })
  api.getProviderDetail.mockResolvedValue({ provider: config, providerHealth: row.providerHealth })
  api.createProvider.mockResolvedValue(config)
  api.updateProvider.mockResolvedValue(config)
  api.deleteProvider.mockResolvedValue(undefined)
  api.testConnection.mockResolvedValue({ connected: true, latencyMs: 12 })
})
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks(); document.body.innerHTML = '' })
async function openPage() {
  wrapper = mount(ProviderList, { global: { plugins: [ElementPlus], stubs: { teleport: true, ElSelect: true, ElOption: true } } })
  await flushPromises()
}
function button(text: string) { return wrapper.findAll('button').find(item => item.text().includes(text))! }

describe('ProviderList real API integration', () => {
  it('loads real rows and displays four health tags including zero latency', async () => {
    const statuses = ['UP', 'DOWN', 'DEGRADED', 'UNKNOWN'] as const
    api.getProviderList.mockResolvedValue({ items: statuses.map((status, index) => ({ ...row, publicId: `provider-${index}`, providerHealth: { status, latencyMs: index === 0 ? 0 : null } })), hasNext: false })
    await openPage()
    expect(api.getProviderList).toHaveBeenCalledWith({ size: 20, cursor: undefined })
    expect(wrapper.text()).toContain('Real provider')
    expect(wrapper.text()).toContain('0 ms')
    for (const [status, type] of [['UP', 'success'], ['DOWN', 'danger'], ['DEGRADED', 'warning'], ['UNKNOWN', 'info']]) {
      expect(wrapper.findAll(`.el-tag--${type}`).some(tag => tag.text() === status)).toBe(true)
    }
  })

  it('reports the capability layer of the connectivity test', async () => {
    const warnings: string[] = []
    const spy = vi.spyOn(ElMessage, 'warning').mockImplementation(message => { warnings.push(String(message)); return { close: () => {} } as never })
    api.testConnection.mockResolvedValue({ connected: true, latencyMs: 12, capabilitySupported: false, capabilityMessage: '模型没有回出工具调用' })
    await openPage()
    await wrapper.get('[data-testid="test-provider-one"]').trigger('click'); await flushPromises()
    expect(warnings.join()).toContain('工具调用不可用')
    expect(warnings.join()).toContain('模型没有回出工具调用')
    spy.mockRestore()
  })

  it('submits create and update with real payload and never prefills saved keys', async () => {
    await openPage()
    await button('新增提供商').trigger('click'); await flushPromises()
    const form = { name: 'New', type: 'openai', baseUrl: 'https://new.example/v1', apiKey: 'test-new', anthropicVersion: '2023-06-01', enabled: true }
    wrapper.getComponent(HifyFormDialog).vm.$emit('submit', form)
    await flushPromises()
    expect(api.createProvider).toHaveBeenCalledWith(expect.objectContaining({ authConfig: { apiKey: 'test-new' } }))
    await wrapper.get('[data-testid="edit-provider-one"]').trigger('click'); await flushPromises()
    const dialog = wrapper.getComponent(HifyFormDialog)
    expect((dialog.get('input[autocomplete="new-password"]').element as HTMLInputElement).value).toBe('')
    dialog.vm.$emit('submit', { ...form, apiKey: '' })
    await flushPromises()
    expect(api.updateProvider).toHaveBeenCalledWith('provider-one', expect.objectContaining({ configVersion: 3, credentialAction: 'KEEP', extraParams: { seed: 1 } }))
  })

  it('deletes using publicId and refreshes the table', async () => {
    await openPage()
    await wrapper.get('[data-testid="delete-provider-one"]').trigger('click'); await flushPromises()
    expect(api.deleteProvider).toHaveBeenCalledWith('provider-one')
    expect(api.getProviderList).toHaveBeenCalledTimes(2)
  })

  it('tests connection once per row while pending and shows ElMessage', async () => {
    let resolve!: (value: { connected: boolean; latencyMs: number }) => void
    api.testConnection.mockReturnValue(new Promise(done => { resolve = done }))
    const success = vi.spyOn(ElMessage, 'success').mockReturnValue({ close: vi.fn() })
    await openPage()
    const testButton = wrapper.get('[data-testid="test-provider-one"]')
    await testButton.trigger('click'); await testButton.trigger('click')
    expect(api.testConnection).toHaveBeenCalledTimes(1)
    resolve({ connected: true, latencyMs: 12 }); await flushPromises()
    expect(success).toHaveBeenCalledWith(expect.stringContaining('12 ms'))
  })

  it('shows model empty state and does not double-toast connection failures', async () => {
    api.getProviderDetail.mockResolvedValue({ provider: { ...config, modelConfigs: [] }, providerHealth: row.providerHealth })
    api.testConnection.mockRejectedValue(new Error('already shown by request interceptor'))
    const error = vi.spyOn(ElMessage, 'error').mockReturnValue({ close: vi.fn() })
    await openPage()
    await wrapper.get('[data-testid="models-provider-one"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('暂无关联模型')
    await wrapper.get('[data-testid="test-provider-one"]').trigger('click'); await flushPromises()
    expect(error).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="test-provider-one"]').classes()).not.toContain('is-loading')
  })

  it('expands model configurations lazily and retries failed loading', async () => {
    api.getProviderDetail.mockRejectedValueOnce(new Error('failed')).mockResolvedValue({ provider: config, providerHealth: row.providerHealth })
    await openPage()
    await wrapper.get('[data-testid="models-provider-one"]').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('模型加载失败')
    await button('重新加载').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('upstream-model')
    expect(api.getProviderDetail).toHaveBeenCalledTimes(2)
  })
})
