import { describe, expect, it, vi } from 'vitest'

const { confirmMock } = vi.hoisted(() => ({ confirmMock: vi.fn() }))

// ElMessageBox 要 DOM，这里换成假的，只记录调用
vi.mock('element-plus', () => ({
  ElMessageBox: {
    confirm: confirmMock,
  },
}))

const { successMock } = vi.hoisted(() => ({ successMock: vi.fn() }))

vi.mock('@/utils/notify', () => ({
  notifySuccess: successMock,
}))

import { useConfirm } from '@/composables/useConfirm'

describe('useConfirm', () => {
  it('确认后调接口并提示成功', async () => {
    confirmMock.mockResolvedValue('confirm')
    const api = vi.fn().mockResolvedValue(undefined)
    const confirm = useConfirm('确定删除这个模型接入点吗？', api)

    await expect(confirm()).resolves.toBe(true)

    expect(api).toHaveBeenCalledOnce()
    expect(successMock).toHaveBeenCalledWith('操作成功')
  })

  it('用户取消：不调接口、不提示', async () => {
    confirmMock.mockRejectedValue('cancel')
    const api = vi.fn()
    const confirm = useConfirm('确定删除？', api)

    await expect(confirm()).resolves.toBe(false)

    expect(api).not.toHaveBeenCalled()
    expect(successMock).not.toHaveBeenCalled()
  })

  it('接口失败：不提示成功', async () => {
    confirmMock.mockResolvedValue('confirm')
    const api = vi.fn().mockRejectedValue(new Error('炸了'))
    const confirm = useConfirm('确定删除？', api)

    await expect(confirm()).resolves.toBe(false)

    expect(successMock).not.toHaveBeenCalled()
  })

  it('自定义文案与成功提示', async () => {
    confirmMock.mockResolvedValue('confirm')
    const api = vi.fn().mockResolvedValue(undefined)
    const confirm = useConfirm('确定？', api, {
      title: '删除模型接入点',
      confirmText: '删掉',
      successText: '已删除',
    })

    await confirm()

    expect(confirmMock).toHaveBeenCalledWith(
      '确定？',
      '删除模型接入点',
      expect.objectContaining({ confirmButtonText: '删掉' }),
    )
    expect(successMock).toHaveBeenCalledWith('已删除')
  })
})
