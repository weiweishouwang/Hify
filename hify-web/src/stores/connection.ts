import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * SSE 连接状态。
 *
 * 整个应用同时只允许一条流式连接（对应一个对话页），
 * 所以这里用一个 AbortController 兜底：重新发送时先掐断上一条。
 */
export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

export const useConnectionStore = defineStore('connection', () => {
  const status = ref<ConnectionStatus>('idle')
  const errorMessage = ref<string | null>(null)

  // 非响应式：只用来持有 AbortController，不需要触发渲染
  let controller: AbortController | null = null

  const isActive = computed(() => status.value === 'connecting' || status.value === 'open')

  /** 开始一条新连接，返回给 fetch 用的 signal。会先掐断上一条。 */
  function begin(): AbortSignal {
    controller?.abort()
    controller = new AbortController()
    status.value = 'connecting'
    errorMessage.value = null
    return controller.signal
  }

  function markOpen(): void {
    status.value = 'open'
  }

  function markClosed(): void {
    status.value = 'closed'
  }

  function markError(message: string): void {
    status.value = 'error'
    errorMessage.value = message
  }

  /** 主动断开（页面卸载、用户点停止）。 */
  function abort(): void {
    controller?.abort()
    controller = null
    if (status.value !== 'idle') {
      status.value = 'closed'
    }
  }

  function reset(): void {
    abort()
    status.value = 'idle'
    errorMessage.value = null
  }

  return {
    status,
    errorMessage,
    isActive,
    begin,
    markOpen,
    markClosed,
    markError,
    abort,
    reset,
  }
})
