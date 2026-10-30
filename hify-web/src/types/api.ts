/**
 * 后端统一响应结构。
 *
 * 与 hify-shared 的 `com.hify.shared.web.Result` 一一对应，字段名不要改。
 * 后端 `code` 前三位等于 HTTP 状态（40000 ↔ 400），0 表示成功。
 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 分页响应：在 Result 基础上多 total / page / size，data 是数组。
 * 对应 `com.hify.shared.web.PageResult`。
 */
export interface PageResult<T> {
  code: number
  message: string
  data: T[]
  total: number
  page: number
  size: number
}

/**
 * 分页请求参数。页码从 1 开始，每页条数上限 100（管理台 200）。
 * 对应 `com.hify.shared.web.PageQuery`。
 */
export interface PageQuery {
  page: number
  size: number
}
