import type { WorkflowCreatePayload, WorkflowStatus, WorkflowUpdatePayload } from '@/types/workflow'

/** 编辑表单。 */
export interface WorkflowForm extends Record<string, unknown> {
  name: string
  description: string
  status: WorkflowStatus
  definition: string
}

export const WORKFLOW_STATUS_OPTIONS: Array<{ value: WorkflowStatus; label: string }> = [
  { value: 'draft', label: '草稿' },
  { value: 'published', label: '已发布' },
  { value: 'disabled', label: '已停用' },
]

export const WORKFLOW_FORM_DEFAULTS: WorkflowForm = {
  name: '',
  description: '',
  status: 'draft',
  definition: '',
}

/**
 * 示例定义。
 *
 * 用的是能跑通的最小结构：模型调用 → 输出。
 * 模型填的是模型管理页里的 publicId —— JSON 里只能写标识，写不了「按名字找」。
 */
export const WORKFLOW_SAMPLE = `{
  "model": "01MODEL000000000000000001",
  "nodes": [
    {
      "id": "n1",
      "type": "llm",
      "name": "生成回答",
      "prompt": "请回答：{{input.question}}",
      "output": "answer"
    },
    {
      "id": "n2",
      "type": "output",
      "name": "返回结果",
      "value": "{{answer}}",
      "output": "result"
    }
  ],
  "edges": [
    { "from": "n1", "to": "n2" }
  ]
}`

/**
 * 格式化定义 JSON。
 *
 * @param text 定义原文
 * @returns 格式化后的文本；不是合法 JSON 时返回 null
 */
export function formatDefinition(text: string): string | null {
  const trimmed = text?.trim()
  if (!trimmed) return null
  try {
    const parsed: unknown = JSON.parse(trimmed)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    return JSON.stringify(parsed, null, 2)
  } catch {
    return null
  }
}

/**
 * 本地粗校验：能解析、且有节点。
 *
 * <p>真正的校验在服务端（图的连通性、无环、字段齐备都在那边判），
 * 这里只是让用户少点一次保存。
 *
 * @param text 定义原文
 * @returns 出错原因；没问题时返回 null
 */
export function validateDefinition(text: string): string | null {
  const trimmed = text?.trim()
  if (!trimmed) return '工作流定义不能为空'
  let parsed: unknown
  try {
    parsed = JSON.parse(trimmed)
  } catch {
    return '不是合法的 JSON'
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return '工作流定义必须是一个 JSON 对象'
  const nodes = (parsed as { nodes?: unknown }).nodes
  if (!Array.isArray(nodes) || nodes.length === 0) return '工作流至少要有一个节点'
  return null
}

export function workflowPayload(form: WorkflowForm): WorkflowCreatePayload {
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    definition: form.definition,
    status: form.status,
  }
}

export function workflowUpdatePayload(form: WorkflowForm, version: number): WorkflowUpdatePayload {
  return { ...workflowPayload(form), version }
}
