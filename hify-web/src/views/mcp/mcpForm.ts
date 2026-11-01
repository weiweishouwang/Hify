import type { McpServerCreatePayload, McpServerSummary, McpServerUpdatePayload, McpTransport } from '@/types/mcp'

export interface McpForm extends Record<string, unknown> {
  name: string
  transport: McpTransport
  /** stdio 的启动命令。 */
  command: string
  /** http 的服务地址。 */
  url: string
  /** stdio 的命令参数，一行一个。 */
  argsText: string
  /** stdio 的环境变量，一行一个 key=value。 */
  envText: string
  /** http 的请求头，一行一个 key=value。 */
  headersText: string
  enabled: boolean
}

/** 一行一个的文本 → 列表。 */
export function parseLines(text: string): string[] {
  if (!text) return []
  return text.split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0)
}

/** 一行一个 key=value 的文本 → 映射。 */
export function parseKeyValues(text: string): Record<string, string> {
  const result: Record<string, string> = {}
  for (const line of parseLines(text)) {
    const index = line.indexOf('=')
    if (index <= 0) continue
    const name = line.slice(0, index).trim()
    const value = line.slice(index + 1).trim()
    if (name) result[name] = value
  }
  return result
}

/** 映射 → 一行一个 key=value 的文本。 */
export function formatKeyValues(map?: Record<string, string>): string {
  if (!map) return ''
  return Object.entries(map).map(([name, value]) => `${name}=${value}`).join('\n')
}

/** 表单 → 创建载荷。 */
export function mcpPayload(form: McpForm): McpServerCreatePayload {
  const base = {
    name: form.name.trim(),
    transport: form.transport,
    enabled: form.enabled,
  }
  if (form.transport === 'stdio') {
    return {
      ...base,
      command: form.command.trim(),
      args: parseLines(form.argsText),
      env: parseKeyValues(form.envText),
    }
  }
  return {
    ...base,
    url: form.url.trim(),
    headers: parseKeyValues(form.headersText),
  }
}

/** 表单 → 更新载荷（带配置版本做乐观锁）。 */
export function mcpUpdatePayload(form: McpForm, original: McpServerSummary): McpServerUpdatePayload {
  const payload = mcpPayload(form)
  const update: McpServerUpdatePayload = {
    name: payload.name,
    transport: payload.transport,
    enabled: payload.enabled,
    configVersion: original.configVersion,
  }
  if (payload.transport === 'stdio') {
    update.command = payload.command
    update.args = payload.args
    update.env = payload.env
  } else {
    update.url = payload.url
    update.headers = payload.headers
  }
  return update
}

/** 详情 → 表单。 */
export function formFromDetail(detail: McpServerSummary): Partial<McpForm> {
  return {
    name: detail.name,
    transport: detail.transport,
    command: detail.command ?? '',
    url: detail.url ?? '',
    argsText: (detail.args ?? []).join('\n'),
    envText: formatKeyValues(detail.env),
    headersText: formatKeyValues(detail.headers),
    enabled: detail.enabled,
  }
}
