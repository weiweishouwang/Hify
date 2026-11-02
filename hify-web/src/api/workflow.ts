import { API_PREFIX, del, get, post, put } from '@/utils/request'
import type {
  WorkflowCreatePayload, WorkflowDetail, WorkflowListQuery, WorkflowPage, WorkflowRunReq,
  WorkflowRunResult, WorkflowRunSummary, WorkflowUpdatePayload,
} from '@/types/workflow'

const ROOT = `${API_PREFIX}/workflows`
/** 工作流里可能有多个模型调用，跑完要等一会儿。 */
const RUN_TIMEOUT_MS = 300_000
const itemUrl = (publicId: string): string => `${ROOT}/${encodeURIComponent(publicId)}`

export function getWorkflowList(query: WorkflowListQuery): Promise<WorkflowPage> {
  return get<WorkflowPage>(ROOT, { params: query })
}

export function getWorkflowDetail(publicId: string): Promise<WorkflowDetail> {
  return get<WorkflowDetail>(itemUrl(publicId))
}

export function createWorkflow(payload: WorkflowCreatePayload): Promise<WorkflowDetail> {
  return post<WorkflowDetail>(ROOT, payload)
}

export function updateWorkflow(publicId: string, payload: WorkflowUpdatePayload): Promise<WorkflowDetail> {
  return put<WorkflowDetail>(itemUrl(publicId), payload)
}

export function deleteWorkflow(publicId: string): Promise<void> {
  return del<void>(itemUrl(publicId))
}

/** 运行工作流。异步派发，要等它跑完。 */
export function runWorkflow(publicId: string, payload: WorkflowRunReq): Promise<WorkflowRunResult> {
  return post<WorkflowRunResult>(`${itemUrl(publicId)}/run`, payload, { timeout: RUN_TIMEOUT_MS })
}

export function getWorkflowRuns(publicId: string, limit?: number): Promise<WorkflowRunSummary[]> {
  return get<WorkflowRunSummary[]>(`${itemUrl(publicId)}/runs`, {
    params: limit ? { limit } : undefined,
  })
}
