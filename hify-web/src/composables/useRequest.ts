import { ref, type Ref } from 'vue'

/**
 * 请求状态管理。
 *
 * 把「发请求 → 转 loading → 存结果 / 存错误」这三件事收在一个 composable 里，
 * 页面就不用每个都写一遍 try / catch / finally。
 *
 * ```ts
 * const { execute, loading, error } = useRequest(saveModel)
 * await execute(payload)
 * ```
 *
 * **失败不抛异常**：请求层（`utils/request.ts`）已经弹过错误提示了，
 * 这里再抛一次只会让调用方多写一层 try/catch。失败时 `execute` 返回 `undefined`，
 * 错误对象放在 `error` 里。
 */
export interface UseRequestResult<T, P extends unknown[]> {
  /** 最近一次成功的结果；还没请求过或失败了是 undefined。 */
  data: Ref<T | undefined>
  /** 请求进行中。 */
  loading: Ref<boolean>
  /** 最近一次失败的原因；成功或还没请求过时是 undefined。 */
  error: Ref<Error | undefined>
  /** 发起请求。返回结果，失败返回 undefined。 */
  execute: (...args: P) => Promise<T | undefined>
}

/**
 * 包装一个接口方法，自动管理 loading / data / error 三态。
 *
 * @param api 接口方法，参数与返回值原样透传
 * @returns data / loading / error / execute
 */
export function useRequest<T, P extends unknown[] = []>(
  api: (...args: P) => Promise<T>,
): UseRequestResult<T, P> {
  const data = ref<T | undefined>(undefined) as Ref<T | undefined>
  const loading = ref(false)
  const error = ref<Error | undefined>(undefined)

  async function execute(...args: P): Promise<T | undefined> {
    loading.value = true
    error.value = undefined
    try {
      const result = await api(...args)
      data.value = result
      return result
    } catch (cause) {
      error.value = cause instanceof Error ? cause : new Error(String(cause))
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
