/** 系统设置页的类型（只读）。 */

export interface AppInfo {
  /** 版本号。 */
  version: string
  /** 构建时间。 */
  buildTime?: string
  /** 启动时间。 */
  startedAt?: string
  /** 已运行秒数。 */
  uptimeSeconds?: number
  /** Java 版本。 */
  javaVersion?: string
  /** 生效的 profile。 */
  profile?: string
}

export interface DependencyStatus {
  /** 依赖名：MySQL / PostgreSQL / Redis。 */
  name: string
  /** 是否可用。 */
  up: boolean
  /** 说明：可用时是延迟，不可用时是原因。 */
  detail?: string
}

export interface Settings {
  app: AppInfo
  dependencies: DependencyStatus[]
  /** 关键运行参数：名称 → 值。 */
  parameters: Record<string, string>
}
