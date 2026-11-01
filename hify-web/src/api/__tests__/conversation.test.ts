import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import {
  createConversation, deleteConversation, getConversationList, getConversationMessages, updateConversation,
} from '../conversation'

beforeEach(() => vi.clearAllMocks())

describe('会话 API 契约', () => {
  it('列表用游标参数', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getConversationList({ size: 20, cursor: 'c1' })
    expect(request.get).toHaveBeenCalledWith('/v1/conversations', { params: { size: 20, cursor: 'c1' } })
  })

  it('消息历史带 limit', async () => {
    request.get.mockResolvedValue([])
    await getConversationMessages('sess-one', 50)
    expect(request.get).toHaveBeenCalledWith('/v1/conversations/sess-one/messages', { params: { limit: 50 } })
  })

  it('消息历史不传 limit 时不带参数', async () => {
    request.get.mockResolvedValue([])
    await getConversationMessages('sess-one')
    expect(request.get).toHaveBeenCalledWith('/v1/conversations/sess-one/messages', { params: undefined })
  })

  it('增删改用公开 ID', async () => {
    await createConversation({ agentPublicId: 'agent-one' })
    await updateConversation('sess-one', { status: 'archived' })
    await deleteConversation('sess-one')
    expect(request.post).toHaveBeenCalledWith('/v1/conversations', { agentPublicId: 'agent-one' })
    expect(request.put).toHaveBeenCalledWith('/v1/conversations/sess-one', { status: 'archived' })
    expect(request.del).toHaveBeenCalledWith('/v1/conversations/sess-one')
  })
})
