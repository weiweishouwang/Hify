import type { CursorPage, CursorQuery } from './api'

/** 工作流状态：草稿 / 已发布 / 已停用。 */
export type WorkflowStatus = 'draft' | 'published' | 'disabled'

/** 六种节点：模型调用 / Agent / 工具 / 知识检索 / 条件分支 / 输出。 */
export type WorkflowNodeType = 'llm' | 'agent' | 'tool' | 'knowledge' | 'if' | 'output'

/** 工作流摘要。 */
export interface WorkflowSummary {
  publicId: string
  name: string
  description?: string
  /** 版本号，每次更新加一；用作乐观锁。 */
  version: number
  status: WorkflowStatus
  nodeCount: number
  createdAt?: string
  updatedAt?: string
}

/** 工作流详情：带定义原文。 */
export interface WorkflowDetail extends WorkflowSummary {
  /** 定义原文（JSON 文本）。 */
  definition: string
}

export interface WorkflowListQuery extends CursorQuery {
  status?: WorkflowStatus
  keyword?: string
}

export type WorkflowPage = CursorPage<WorkflowSummary>

export interface WorkflowCreatePayload {
  name: string
  description?: string
  /** 工作流 JSON 文本（节点与连线）。 */
  definition: string
  status?: WorkflowStatus
}

export interface WorkflowUpdatePayload extends Partial<WorkflowCreatePayload> {
  /** 版本号，用于乐观锁。 */
  version: number
}

export interface WorkflowRunReq {
  /** 入参的 JSON 文本。 */
  input?: string
}

/** 一个节点的执行痕迹。 */
export interface WorkflowStep {
  nodeId: string
  type: string
  name?: string
  output?: string
  latencyMs?: number
}

/** 一次运行的结果。 */
export interface WorkflowRunResult {
  status: 'succeeded' | 'failed'
  output: Record<string, unknown>
  stepCount: number
  latencyMs?: number
  errorMessage?: string
  steps: WorkflowStep[]
}

export interface WorkflowRunSummary {
  id: number
  status: string
  stepCount: number
  latencyMs?: number
  errorMessage?: string
  createdAt?: string
}
