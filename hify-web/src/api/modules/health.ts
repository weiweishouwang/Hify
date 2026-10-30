import { API_PREFIX, get } from '@/utils/request'

/** GET /api/v1/health —— 后端存活探针，返回一句状态文案。 */
export function getHealth(): Promise<string> {
  return get<string>(`${API_PREFIX}/health`)
}
