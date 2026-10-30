import { ElMessageBox } from 'element-plus'
import { notifySuccess } from '@/utils/notify'

/** 确认框的文案与样式配置。 */
export interface UseConfirmOptions {
  /** 确认框标题。 */
  title?: string
  /** 确认按钮文字。 */
  confirmText?: string
  /** 取消按钮文字。 */
  cancelText?: string
  /** 图标类型。删除类操作用 warning。 */
  type?: 'warning' | 'info' | 'error' | 'success'
  /** 操作成功后的提示文案。 */
  successText?: string
}

/**
 * 删除确认。
 *
 * 把「弹确认框 → 确认后调接口 → 提示成功」收成一次调用，页面里不再写
 * try/catch/ElMessageBox/ElMessage 这一套样板。
 *
 * ```ts
 * const confirmDelete = useConfirm('确定删除这个模型接入点吗？', () => deleteModel(id))
 *
 * async function handleDelete(row: ModelRow) {
 *   if (await confirmDelete()) {
 *     await tableRef.value?.refresh()
 *   }
 * }
 * ```
 *
 * **取消与失败都不抛异常**：用户取消是正常操作；接口失败已经由请求层弹过错误提示了。
 * 两种情况都返回 false，调用方据此决定要不要刷新列表。
 */
export function useConfirm(
  message: string,
  api: () => Promise<unknown>,
  options: UseConfirmOptions = {},
): () => Promise<boolean> {
  return async (): Promise<boolean> => {
    try {
      await ElMessageBox.confirm(message, options.title ?? '请确认', {
        type: options.type ?? 'warning',
        confirmButtonText: options.confirmText ?? '删除',
        cancelButtonText: options.cancelText ?? '取消',
        confirmButtonClass: 'hify-confirm__confirm',
        customClass: 'hify-confirm',
        // 点遮罩与按 ESC 都算取消
        closeOnClickModal: false,
      })
    } catch {
      // 用户取消
      return false
    }

    try {
      await api()
    } catch {
      // 接口失败：错误提示已由请求层弹出，这里不再重复提示
      return false
    }

    notifySuccess(options.successText ?? '操作成功')
    return true
  }
}
