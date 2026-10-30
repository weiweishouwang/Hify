import { describe, expect, it, vi } from 'vitest'
import { useRequest } from '@/composables/useRequest'

describe('useRequest', () => {
  it('成功后：data 有值、loading 归位、error 为空', async () => {
    const { data, loading, error, execute } = useRequest(async (n: number) => n * 2)

    const pending = execute(21)
    // 请求进行中 loading 必须是 true
    expect(loading.value).toBe(true)

    await pending

    expect(data.value).toBe(42)
    expect(loading.value).toBe(false)
    expect(error.value).toBeUndefined()
  })

  it('失败后：error 有值、data 为空、loading 归位，且不抛异常', async () => {
    const { data, loading, error, execute } = useRequest(async () => {
      throw new Error('炸了')
    })

    await expect(execute()).resolves.toBeUndefined()

    expect(error.value?.message).toBe('炸了')
    expect(data.value).toBeUndefined()
    expect(loading.value).toBe(false)
  })

  it('下一次执行会清掉上一次的错误', async () => {
    let shouldFail = true
    const { error, execute } = useRequest(async () => {
      if (shouldFail) {
        throw new Error('第一次失败')
      }
      return 'ok'
    })

    await execute()
    expect(error.value).toBeDefined()

    shouldFail = false
    await execute()

    expect(error.value).toBeUndefined()
  })

  it('原样透传参数', async () => {
    const api = vi.fn(async (a: string, b: number) => `${a}-${b}`)
    const { execute } = useRequest(api)

    await execute('x', 1)

    expect(api).toHaveBeenCalledWith('x', 1)
  })
})
