import { beforeEach, describe, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/utils/request', () => ({ ...request, API_PREFIX: '/v1' }))
import {
  createWorkflow, deleteWorkflow, getWorkflowDetail, getWorkflowList, getWorkflowRuns, runWorkflow,
  updateWorkflow,
} from '../workflow'
import {
  formatDefinition, validateDefinition, workflowPayload, workflowUpdatePayload, WORKFLOW_SAMPLE,
} from '@/views/workflow/workflowForm'

beforeEach(() => vi.clearAllMocks())

describe('工作流 API 契约', () => {
  it('列表用游标参数', async () => {
    request.get.mockResolvedValue({ items: [], hasNext: false })
    await getWorkflowList({ size: 20, cursor: 'c1' })
    expect(request.get).toHaveBeenCalledWith('/v1/workflows', { params: { size: 20, cursor: 'c1' } })
  })

  it('增删改用公开 ID', async () => {
    await createWorkflow({ name: '售后', definition: '{}' })
    await updateWorkflow('wf-one', { name: '售后2', version: 3 })
    await deleteWorkflow('wf-one')
    expect(request.post).toHaveBeenCalledWith('/v1/workflows', { name: '售后', definition: '{}' })
    expect(request.put).toHaveBeenCalledWith('/v1/workflows/wf-one', { name: '售后2', version: 3 })
    expect(request.del).toHaveBeenCalledWith('/v1/workflows/wf-one')
  })

  it('运行与运行记录', async () => {
    request.post.mockResolvedValue({ status: 'succeeded', output: {}, stepCount: 2, steps: [] })
    request.get.mockResolvedValue([])
    await runWorkflow('wf-one', { input: '{"question":"你好"}' })
    await getWorkflowRuns('wf-one', 10)
    expect(request.post).toHaveBeenCalledWith('/v1/workflows/wf-one/run',
      { input: '{"question":"你好"}' }, expect.objectContaining({ timeout: 300_000 }))
    expect(request.get).toHaveBeenCalledWith('/v1/workflows/wf-one/runs', { params: { limit: 10 } })
  })

  it('取详情', async () => {
    request.get.mockResolvedValue({})
    await getWorkflowDetail('wf-one')
    expect(request.get).toHaveBeenCalledWith('/v1/workflows/wf-one')
  })
})

describe('工作流表单与定义处理', () => {
  it('格式化定义 JSON', () => {
    const formatted = formatDefinition('{"a":1}')
    expect(formatted).toContain('\n')
    expect(formatted).toContain('"a": 1')
  })

  it('不是合法 JSON 时格式化返回 null', () => {
    expect(formatDefinition('{不是 JSON')).toBeNull()
    expect(formatDefinition('[]')).toBeNull()
    expect(formatDefinition('')).toBeNull()
  })

  it('本地粗校验', () => {
    expect(validateDefinition('')).toBe('工作流定义不能为空')
    expect(validateDefinition('{不是 JSON')).toBe('不是合法的 JSON')
    expect(validateDefinition('{"nodes":[]}')).toBe('工作流至少要有一个节点')
    expect(validateDefinition(WORKFLOW_SAMPLE)).toBeNull()
  })

  it('提交载荷带名称、定义与状态', () => {
    const payload = workflowPayload({
      name: ' 售后流程 ', description: ' 处理售后 ', status: 'published', definition: '{}',
    })
    expect(payload.name).toBe('售后流程')
    expect(payload.description).toBe('处理售后')
    expect(payload.status).toBe('published')
  })

  it('更新载荷带上配置版本', () => {
    const payload = workflowUpdatePayload({
      name: 'n', description: '', status: 'draft', definition: '{}',
    }, 4)
    expect(payload.version).toBe(4)
  })
})
