import type { AgentCreatePayload, AgentDetail, AgentParamConfig, AgentStatus, AgentUpdatePayload } from '@/types/agent'

/** Agent 的编辑表单。参数区摊平成三个字段，提交时再收进 paramConfig。 */
export interface AgentForm extends Record<string, unknown> {
  name: string
  description: string
  modelPublicId: string
  promptTemplate: string
  toolIds: string[]
  knowledgeIds: string[]
  temperature?: number
  maxTokens?: number
  topP?: number
  status: AgentStatus
}

export const AGENT_STATUS_OPTIONS: Array<{ value: AgentStatus; label: string }> = [
  { value: 'draft', label: '草稿（对话页选不到）' },
  { value: 'published', label: '已发布（可用）' },
  { value: 'disabled', label: '已停用' },
]

export const AGENT_FORM_DEFAULTS: AgentForm = {
  name: '',
  description: '',
  modelPublicId: '',
  promptTemplate: '',
  toolIds: [],
  knowledgeIds: [],
  status: 'draft',
}

/**
 * 参数区：只保留填了的字段。
 *
 * <p><b>永远返回一个对象</b>（哪怕是空的）—— 编辑时「把三个参数全清空」
 * 也得能把服务端的值清掉。用「不传这个字段」表示「保持原值」的话，
 * 清空操作就永远传不出去。
 */
export function paramConfig(form: AgentForm): AgentParamConfig {
  const config: AgentParamConfig = {}
  if (typeof form.temperature === 'number' && !Number.isNaN(form.temperature)) {
    config.temperature = form.temperature
  }
  if (typeof form.maxTokens === 'number' && !Number.isNaN(form.maxTokens)) {
    config.maxTokens = form.maxTokens
  }
  if (typeof form.topP === 'number' && !Number.isNaN(form.topP)) {
    config.topP = form.topP
  }
  return config
}

export function agentPayload(form: AgentForm): AgentCreatePayload
export function agentPayload(form: AgentForm, original: { configVersion: number }): AgentUpdatePayload
export function agentPayload(form: AgentForm, original?: { configVersion: number }): AgentCreatePayload | AgentUpdatePayload {
  const base = {
    name: form.name.trim(),
    description: form.description.trim(),
    modelPublicId: form.modelPublicId,
    promptTemplate: form.promptTemplate,
    toolIds: [...form.toolIds],
    knowledgeIds: [...form.knowledgeIds],
    paramConfig: paramConfig(form),
    status: form.status,
  }
  if (!original) {
    return base
  }
  return { ...base, configVersion: original.configVersion }
}

/** 编辑时用详情回填表单。 */
export function formFromDetail(detail: AgentDetail): Partial<AgentForm> {
  return {
    name: detail.name,
    description: detail.description ?? '',
    modelPublicId: detail.modelPublicId ?? '',
    promptTemplate: detail.promptTemplate ?? '',
    toolIds: [...(detail.toolIds ?? [])],
    knowledgeIds: [...(detail.knowledgeIds ?? [])],
    temperature: detail.paramConfig?.temperature,
    maxTokens: detail.paramConfig?.maxTokens,
    topP: detail.paramConfig?.topP,
    status: detail.status,
  }
}
