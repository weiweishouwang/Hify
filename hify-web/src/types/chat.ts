/** 消息角色。tool 是工具调用产生的结果消息。 */
export type MessageRole = 'user' | 'assistant' | 'system' | 'tool'

/** 消息状态。pending 还没开始，streaming 正在流式输出，done 已完成，error 出错。 */
export type MessageStatus = 'pending' | 'streaming' | 'done' | 'error'

/** 一次工具调用的状态。 */
export type ToolCallStatus = 'running' | 'done' | 'error'

export interface ToolCall {
  id: string
  name: string
  /** 入参，可能是未完成的 JSON 片段（流式场景下逐段到达）。 */
  arguments: string
  /** 执行结果，工具执行完成后才有。 */
  result?: string
  status: ToolCallStatus
}

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  status: MessageStatus
  createdAt: string
  toolCalls?: ToolCall[]
}
