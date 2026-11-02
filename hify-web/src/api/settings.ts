import { API_PREFIX, get } from '@/utils/request'
import type { Settings } from '@/types/settings'

/** 系统设置（只读）。 */
export function getSettings(): Promise<Settings> {
  return get<Settings>(`${API_PREFIX}/settings`)
}
