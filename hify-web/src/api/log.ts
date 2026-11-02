import { API_PREFIX, get } from '@/utils/request'
import type { ConversationRunPage, RunLogQuery, WorkflowRunLogPage } from '@/types/log'

/**
 * 控制台「日志」页的接口。
 *
 * 两类记录分属两个模块，各走各的接口 —— 前端并排看，不做跨模块聚合。
 */
const CONVERSATION_RUNS = `${API_PREFIX}/conversations/runs`
const WORKFLOW_RUNS = `${API_PREFIX}/workflows/runs`

/** 对话运行记录。 */
export function getConversationRuns(query: RunLogQuery): Promise<ConversationRunPage> {
  return get<ConversationRunPage>(CONVERSATION_RUNS, { params: query })
}

/** 工作流运行记录。 */
export function getWorkflowRuns(query: RunLogQuery): Promise<WorkflowRunLogPage> {
  return get<WorkflowRunLogPage>(WORKFLOW_RUNS, { params: query })
}
