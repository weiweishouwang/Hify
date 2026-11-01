// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationMessage, ConversationSummary } from '@/types/conversation'

const conversationApi = vi.hoisted(() => ({
  getConversationList: vi.fn(), getConversationDetail: vi.fn(), getConversationMessages: vi.fn(),
  createConversation: vi.fn(), updateConversation: vi.fn(), deleteConversation: vi.fn(),
}))
const agentApi = vi.hoisted(() => ({ getAgentList: vi.fn() }))
const chatApi = vi.hoisted(() => ({ streamChat: vi.fn() }))
vi.mock('@/api/conversation', () => conversationApi)
vi.mock('@/api/agent', () => agentApi)
vi.mock('@/api/chat', () => chatApi)
import ChatView from '../ChatView.vue'

const session: ConversationSummary = {
  publicId: 'sess-one', title: '订单问题', agentPublicId: 'agent-one', agentName: '客服助手',
  messageCount: 2, status: 'active',
}
/** 模拟服务端的消息历史：流结束后前端会重新拉一次，界面以服务端为准。 */
let history: ConversationMessage[] = []
let wrapper: ReturnType<typeof mount>

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  history = [
    { id: 1, role: 'user', content: '订单在哪' },
    { id: 2, role: 'assistant', content: '在配送中' },
  ]
  conversationApi.getConversationList.mockResolvedValue({ items: [session], hasNext: false })
  conversationApi.getConversationMessages.mockImplementation(async () => history.map((item) => ({ ...item })))
  conversationApi.deleteConversation.mockResolvedValue(undefined)
  agentApi.getAgentList.mockResolvedValue({
    items: [{ publicId: 'agent-one', name: '客服助手', status: 'published' }], hasNext: false,
  })
  chatApi.streamChat.mockResolvedValue(undefined)
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  document.body.innerHTML = ''
})

async function openPage() {
  wrapper = mount(ChatView, { global: { plugins: [ElementPlus, createPinia()] } })
  await flushPromises()
}

describe('对话页真实接口', () => {
  it('加载会话列表并默认选中第一个', async () => {
    await openPage()
    expect(conversationApi.getConversationList).toHaveBeenCalledWith({ size: 50 })
    expect(wrapper.text()).toContain('订单问题')
    expect(wrapper.text()).toContain('客服助手')
    expect(conversationApi.getConversationMessages).toHaveBeenCalledWith('sess-one')
  })

  it('把历史消息渲染成气泡', async () => {
    await openPage()
    expect(wrapper.text()).toContain('订单在哪')
    expect(wrapper.text()).toContain('在配送中')
  })

  it('发送消息后走流式接口并把增量写进气泡', async () => {
    chatApi.streamChat.mockImplementation(async (_id, content, handlers) => {
      history.push({ id: 3, role: 'user', content })
      handlers.onTurn?.(0)
      handlers.onText('您好')
      handlers.onText('，在查')
      handlers.onDone?.('stop')
      history.push({ id: 4, role: 'assistant', content: '您好，在查' })
    })
    await openPage()
    await wrapper.find('textarea').setValue('现在几点')
    await wrapper.get('[data-testid="send"]').trigger('click')
    await flushPromises()

    expect(chatApi.streamChat).toHaveBeenCalled()
    expect(chatApi.streamChat.mock.calls[0][0]).toBe('sess-one')
    expect(chatApi.streamChat.mock.calls[0][1]).toBe('现在几点')
    expect(wrapper.text()).toContain('您好，在查')
  })

  it('工具调用挂成一张卡片', async () => {
    chatApi.streamChat.mockImplementation(async (_id, content, handlers) => {
      handlers.onTurn?.(0)
      handlers.onToolCall?.({ id: 'call-1', name: '查订单', arguments: '{"no":"A1"}', status: 'running' })
      handlers.onToolResult?.({
        id: 'call-1', name: '查订单', arguments: '{"no":"A1"}', result: '在配送中', status: 'done',
      })
      handlers.onText('找到了')
      handlers.onDone?.('stop')
      history.push({ id: 3, role: 'user', content })
      history.push({
        id: 4, role: 'assistant', content: '找到了',
        toolCalls: [{ id: 'call-1', name: '查订单', arguments: '{"no":"A1"}', result: '在配送中' }],
      })
    })
    await openPage()
    await wrapper.find('textarea').setValue('订单在哪')
    await wrapper.get('[data-testid="send"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('查订单')
    expect(wrapper.text()).toContain('在配送中')
  })

  it('删除会话走确认弹窗与真实接口', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    await openPage()
    await wrapper.get('[data-testid="delete-sess-one"]').trigger('click')
    await flushPromises()
    expect(conversationApi.deleteConversation).toHaveBeenCalledWith('sess-one')
  })
})
