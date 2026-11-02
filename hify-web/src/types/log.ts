import type { CursorPage, CursorQuery } from './api'

/**
 * 控制台「日志」页的类型。
 *
 * 两类记录：对话的每一轮运行、工作流的每一次运行。
 * 它们分属两个模块，各自有自己的列表接口 —— 日志页只是把两边并排看。
 */

/** 运行状态：running / succeeded / failed。 */
export type RunStatus = 'running' | 'succeeded' | 'failed'

/** 一轮对话的运行记录。 */
export interface ConversationRun {
  id: number
  /** 会话的对外 ID。 */
  sessionPublicId: string
  /** 会话标题。 */
  sessionTitle: string
  /** 使用的 Agent 名字；Agent 被删了就空着。 */
  agentName?: string
  status: RunStatus
  promptTokens?: number
  completionTokens?: number
  /** 工具调用次数。 */
  toolCallCount?: number
  /** 耗时（毫秒）。 */
  latencyMs?: number
  /** 失败原因；成功时为空。 */
  errorMessage?: string
  createdAt?: string
}

export type ConversationRunPage = CursorPage<ConversationRun>

/** 一次工作流运行的记录。 */
export interface WorkflowRunLog {
  id: number
  /** 工作流的对外 ID。 */
  definitionPublicId?: string
  /** 工作流名字；工作流被删了就空着。 */
  definitionName?: string
  status: RunStatus
  /** 执行了几个节点。 */
  stepCount?: number
  /** 耗时（毫秒）。 */
  latencyMs?: number
  /** 入参摘要（截断后的 JSON）。 */
  inputPreview?: string
  /** 出参摘要（截断后的 JSON）。 */
  outputPreview?: string
  /** 失败原因；成功时为空。 */
  errorMessage?: string
  createdAt?: string
}

export type WorkflowRunLogPage = CursorPage<WorkflowRunLog>

export interface RunLogQuery extends CursorQuery {
  status?: RunStatus
}
