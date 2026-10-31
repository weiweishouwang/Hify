import type { ToolCreatePayload, ToolDetail, ToolMethod, ToolUpdatePayload } from '@/types/tool'

/** 请求头编辑器的每一行。 */
export interface HeaderRow extends Record<string, unknown> {
  name: string
  value: string
}

export interface ToolForm extends Record<string, unknown> {
  name: string
  toolKey: string
  description: string
  url: string
  method: ToolMethod
  /** 入参 JSON Schema 的文本形式，提交前解析成对象。 */
  schemaText: string
  /** 请求头按行编辑；敏感头的值是占位符，提交时原样回传表示保留。 */
  headers: HeaderRow[]
  timeoutMs?: number
  enabled: boolean
}

export const SCHEMA_EXAMPLE = `{
  "type": "object",
  "properties": {
    "city": { "type": "string", "description": "城市名" }
  },
  "required": ["city"]
}`

/** 把详情里的请求头映射拆成编辑用的行。 */
export function headerRows(headers?: Record<string, string>): HeaderRow[] {
  if (!headers) return []
  return Object.entries(headers).map(([name, value]) => ({ name, value }))
}

/**
 * 把编辑用的行合并成请求头。
 *
 * 空的行会被丢掉 —— 这正好对应后端「提交空串 = 不设置这个头」的语义：
 * 用户清空某一行就等于删掉这个请求头。
 */
export function headerMap(rows: HeaderRow[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const row of rows) {
    const name = row.name?.trim()
    const value = row.value?.trim()
    if (!name || !value) continue
    result[name] = value
  }
  return result
}

/** 解析入参 schema 的文本形式；不是合法 JSON 对象时返回 undefined。 */
export function parseSchema(schemaText: string): Record<string, unknown> | undefined {
  const text = schemaText?.trim()
  if (!text) return undefined
  try {
    const parsed: unknown = JSON.parse(text)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return undefined
    return parsed as Record<string, unknown>
  } catch {
    return undefined
  }
}

export function toolPayload(form: ToolForm): ToolCreatePayload
export function toolPayload(form: ToolForm, original: { configVersion: number }): ToolUpdatePayload
export function toolPayload(form: ToolForm, original?: { configVersion: number }): ToolCreatePayload | ToolUpdatePayload {
  const schema = parseSchema(form.schemaText)
  const headers = headerMap(form.headers ?? [])
  const base = {
    name: form.name.trim(),
    description: form.description.trim(),
    url: form.url.trim(),
    method: form.method,
    headers,
    timeoutMs: form.timeoutMs,
  }
  if (!original) {
    return {
      ...base,
      sourceType: 'http' as const,
      toolKey: form.toolKey.trim(),
      ...(schema ? { schema } : {}),
    }
  }
  return {
    ...base,
    enabled: form.enabled,
    configVersion: original.configVersion,
    ...(schema ? { schema } : {}),
  }
}

/** 编辑时用详情回填表单。工具标识不可改。 */
export function formFromDetail(detail: ToolDetail): Partial<ToolForm> {
  return {
    name: detail.name,
    toolKey: detail.toolKey,
    description: detail.description ?? '',
    url: detail.url ?? '',
    method: detail.method ?? 'POST',
    schemaText: detail.schema ? JSON.stringify(detail.schema, null, 2) : '',
    headers: headerRows(detail.headers),
    timeoutMs: detail.timeoutMs,
    enabled: detail.enabled,
  }
}
