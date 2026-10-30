import { request } from '../client'

/** GET /api/v1/health —— 后端存活探针，返回一句状态文案。 */
export function getHealth(): Promise<string> {
  return request<string>({ method: 'GET', url: '/health' })
}
