// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Settings } from '@/types/settings'

const api = vi.hoisted(() => ({ getSettings: vi.fn() }))
vi.mock('@/api/settings', () => api)
import SettingsView from '../SettingsView.vue'

const settings: Settings = {
  app: {
    version: '0.1.0-SNAPSHOT', buildTime: '2026-11-02T03:00:00Z', startedAt: '2026-11-02T03:10:00',
    uptimeSeconds: 3661, javaVersion: '21.0.7', profile: 'prod',
  },
  dependencies: [
    { name: 'MySQL', up: true, detail: '2 ms' },
    { name: 'PostgreSQL', up: true, detail: '1 ms' },
    { name: 'Redis', up: false, detail: 'Connection refused' },
  ],
  parameters: { 'HTTP · Tomcat 线程数': '200' },
}

let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  vi.clearAllMocks()
  api.getSettings.mockResolvedValue(settings)
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

async function openPage() {
  wrapper = mount(SettingsView, { global: { plugins: [ElementPlus], stubs: { teleport: true } } })
  await flushPromises()
}

describe('SettingsView', () => {
  it('shows version, uptime and runtime parameters', async () => {
    await openPage()
    expect(api.getSettings).toHaveBeenCalled()
    expect(wrapper.text()).toContain('0.1.0-SNAPSHOT')
    expect(wrapper.text()).toContain('1 小时 1 分钟')
    expect(wrapper.text()).toContain('21.0.7')
    expect(wrapper.text()).toContain('200')
  })

  it('shows each dependency with its status', async () => {
    await openPage()
    expect(wrapper.text()).toContain('MySQL')
    expect(wrapper.text()).toContain('PostgreSQL')
    expect(wrapper.text()).toContain('Redis')
    expect(wrapper.text()).toContain('不可用')
    expect(wrapper.findAll('.el-tag--danger').length).toBe(1)
  })
})
