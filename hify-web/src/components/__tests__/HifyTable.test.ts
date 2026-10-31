// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HifyTable, { type HifyTableHandle } from '../HifyTable.vue'

const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); wrappers.length = 0 })
const columns = [{ label: 'Name', prop: 'name' }]
function button(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find(item => item.text().includes(text))!
}

describe('HifyTable cursor pagination', () => {
  it('uses real next cursors, previous cursors and reset without inventing total', async () => {
    const api = vi.fn().mockResolvedValueOnce({ items: [{ id: 1, name: 'one' }], hasNext: true, nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: [{ id: 2, name: 'two' }], hasNext: false })
      .mockResolvedValue({ items: [{ id: 1, name: 'one' }], hasNext: true, nextCursor: 'c1' })
    const wrapper = mount(HifyTable, { props: { columns, api, paginationMode: 'cursor', pageSize: 10 }, global: { plugins: [ElementPlus] } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(api).toHaveBeenLastCalledWith({ size: 10, cursor: undefined })
    expect(wrapper.find('.el-pagination').exists()).toBe(false)
    await button(wrapper, '下一页').trigger('click'); await flushPromises()
    expect(api).toHaveBeenLastCalledWith({ size: 10, cursor: 'c1' })
    expect(button(wrapper, '下一页').attributes('disabled')).toBeDefined()
    await button(wrapper, '上一页').trigger('click'); await flushPromises()
    expect(api).toHaveBeenLastCalledWith({ size: 10, cursor: undefined })
    expect(button(wrapper, '上一页').attributes('disabled')).toBeDefined()
  })

  it('does not represent request errors as an empty success and supports retry', async () => {
    const api = vi.fn().mockRejectedValueOnce(new Error('failure')).mockResolvedValue({ items: [], hasNext: false })
    const wrapper = mount(HifyTable, { props: { columns, api, paginationMode: 'cursor' }, global: { plugins: [ElementPlus] } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.text()).toContain('加载失败')
    await button(wrapper, '重试').trigger('click'); await flushPromises()
    expect(api).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('暂无数据')
  })

  it('ignores stale responses and resets cursor position on refresh', async () => {
    let finish!: (value: { items: { id: number; name: string }[]; hasNext: boolean }) => void
    const api = vi.fn().mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
      .mockResolvedValue({ items: [{ id: 2, name: 'new-response' }], hasNext: false })
    const wrapper = mount(HifyTable, { props: { columns, api, paginationMode: 'cursor' }, global: { plugins: [ElementPlus] } })
    wrappers.push(wrapper)
    const handle = wrapper.vm.$.exposed as unknown as HifyTableHandle<{ id: number; name: string }>
    await handle.refresh({ reset: true }); await flushPromises()
    finish({ items: [{ id: 1, name: 'old-response' }], hasNext: false }); await flushPromises()
    expect(wrapper.text()).toContain('new-response')
    expect(wrapper.text()).not.toContain('old-response')
    expect(api).toHaveBeenLastCalledWith({ size: 20, cursor: undefined })
  })

  it('keeps the existing page API behavior', async () => {
    const api = vi.fn().mockResolvedValue({ data: [], total: 0 })
    const wrapper = mount(HifyTable, { props: { columns, api }, global: { plugins: [ElementPlus] } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(api).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(wrapper.find('.el-pagination').exists()).toBe(true)
  })
})
