import { describe, expect, it, vi } from 'vitest'

const { messageMock } = vi.hoisted(() => ({ messageMock: vi.fn() }))

// ElMessage 要 DOM，这里换成假的，只记录调用
vi.mock('element-plus', () => ({
  ElMessage: messageMock,
}))

import { notifyError, notifySuccess, notifyWarning } from '@/utils/notify'

describe('notify', () => {
  it('成功提示：统一时长 2.5 秒', () => {
    notifySuccess('保存成功')

    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ message: '保存成功', type: 'success', duration: 2500 }),
    )
  })

  it('错误提示：时长更长（4 秒）且可关闭', () => {
    notifyError('保存失败')

    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({
        message: '保存失败',
        type: 'error',
        duration: 4000,
        showClose: true,
      }),
    )
  })

  it('警告提示', () => {
    notifyWarning('这个操作不可撤销')

    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ message: '这个操作不可撤销', type: 'warning', duration: 2500 }),
    )
  })

  it('时长可以单独覆盖', () => {
    notifySuccess('马上消失', 800)

    expect(messageMock).toHaveBeenCalledWith(expect.objectContaining({ duration: 800 }))
  })
})
