import { ElMessage } from 'element-plus'

/**
 * 统一通知封装。
 *
 * 底层调用 Element Plus 的 `ElMessage`，但时长与外观在这里统一定死，
 * 免得每个页面各写各的 `ElMessage.success({...})`。
 *
 * **失败提示不要用这里** —— `utils/request.ts` 的响应拦截器已经弹过错误了，
 * 再弹一次就是两次 toast（CLAUDE.md §3.6）。
 */

/** 成功 / 警告的默认停留时长（毫秒）。 */
const DEFAULT_DURATION = 2500

/** 错误的默认停留时长（毫秒）—— 比成功的长，用户要来得及看清。 */
const ERROR_DURATION = 4000

/** 统一的样式钩子，对应 index.css 里的 `.hify-notify`。 */
const NOTIFY_CLASS = 'hify-notify'

/**
 * 成功提示。
 *
 * @param message  提示文案
 * @param duration 停留时长（毫秒）
 */
export function notifySuccess(message: string, duration: number = DEFAULT_DURATION): void {
  ElMessage({ message, type: 'success', duration, customClass: NOTIFY_CLASS })
}

/**
 * 失败提示。
 *
 * **接口失败不要调它** —— 请求拦截器已经弹过了。
 * 这里用于前端自己发现的失败（校验没过、本地判断不通过之类）。
 *
 * @param message  提示文案
 * @param duration 停留时长（毫秒）
 */
export function notifyError(message: string, duration: number = ERROR_DURATION): void {
  ElMessage({ message, type: 'error', duration, showClose: true, customClass: NOTIFY_CLASS })
}

/**
 * 警告提示。
 *
 * @param message  提示文案
 * @param duration 停留时长（毫秒）
 */
export function notifyWarning(message: string, duration: number = DEFAULT_DURATION): void {
  ElMessage({ message, type: 'warning', duration, customClass: NOTIFY_CLASS })
}
