import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import { createAgent, deleteAgent, getAgentList, getAgentOptions, updateAgent } from '../agent'
import { agentPayload, formFromDetail, paramConfig, type AgentForm } from '@/views/agent/agentForm'
import type { AgentDetail } from '@/types/agent'

beforeEach(() => vi.clearAllMocks())

describe('Agent API contract', () => {
  it('uses cursor params for the list', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getAgentList({ size: 10, cursor: 'cursor' })
    expect(request.get).toHaveBeenCalledWith('/v1/agents', { params: { size: 10, cursor: 'cursor' } })
  })

  it('uses public IDs for update and deletion', async () => {
    await updateAgent('agent-one', { name: '新名字', configVersion: 2 })
    await deleteAgent('agent-one')
    expect(request.put).toHaveBeenCalledWith('/v1/agents/agent-one', expect.objectContaining({ configVersion: 2 }))
    expect(request.del).toHaveBeenCalledWith('/v1/agents/agent-one')
  })

  it('creates an agent with the bindings as id arrays', async () => {
    await createAgent({
      name: '客服', modelPublicId: 'model-one', toolIds: ['tool-one'], knowledgeIds: ['kb-one'],
    })
    expect(request.post).toHaveBeenCalledWith('/v1/agents', expect.objectContaining({
      name: '客服', modelPublicId: 'model-one', toolIds: ['tool-one'], knowledgeIds: ['kb-one'],
    }))
  })

  it('reads the selectable options from the aggregation endpoint', async () => {
    request.get.mockResolvedValue({ models: [], tools: [], knowledgeBases: [], truncated: false })
    await getAgentOptions()
    expect(request.get).toHaveBeenCalledWith('/v1/agents/options')
  })
})

describe('Agent form payload rules', () => {
  const form: AgentForm = {
    name: ' 客服助手 ', description: ' 处理售后 ', modelPublicId: 'model-one',
    promptTemplate: '你是一个客服', toolIds: ['t1'], knowledgeIds: ['k1'],
    temperature: 0.3, status: 'published',
  }

  it('trims the text fields and keeps the id arrays', () => {
    const payload = agentPayload(form)
    expect(payload.name).toBe('客服助手')
    expect(payload.description).toBe('处理售后')
    expect(payload.toolIds).toEqual(['t1'])
    expect(payload.knowledgeIds).toEqual(['k1'])
  })

  it('only sends the params that were filled in, and always sends the object', () => {
    expect(paramConfig(form)).toEqual({ temperature: 0.3 })
    // 全清空时也要把 paramConfig 发出去，否则服务端会当成「保持原值」
    expect(paramConfig({ ...form, temperature: undefined })).toEqual({})
    expect(agentPayload(form).paramConfig).toEqual({ temperature: 0.3 })
  })

  it('carries the config version on update', () => {
    const update = agentPayload(form, { configVersion: 5 })
    expect(update.configVersion).toBe(5)
    expect(update.name).toBe('客服助手')
  })

  it('prefills the edit form from the detail', () => {
    const detail: AgentDetail = {
      publicId: 'agent-one', name: '客服助手', description: '处理售后',
      modelPublicId: 'model-one', modelDisplayName: '通义千问',
      promptTemplate: '你是一个客服', toolIds: ['t1'], knowledgeIds: ['k1'],
      paramConfig: { temperature: 0.3, maxTokens: 2048 },
      status: 'published', appType: 'chat', unavailableCount: 0, configVersion: 3,
    }
    const values = formFromDetail(detail)
    expect(values.name).toBe('客服助手')
    expect(values.modelPublicId).toBe('model-one')
    expect(values.toolIds).toEqual(['t1'])
    expect(values.temperature).toBe(0.3)
    expect(values.maxTokens).toBe(2048)
    expect(values.status).toBe('published')
  })
})
