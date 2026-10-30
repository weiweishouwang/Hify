import { onUnmounted, ref, type Ref } from 'vue'

/**
 * 窄屏断点：小于 1200px。
 *
 * 侧边栏在这个宽度以下自动折叠成只显示图标；表格也据此隐藏次要列。
 */
export const NARROW_MEDIA = '(max-width: 1199px)'

/**
 * 媒体查询的值，随屏幕宽度变化。
 *
 * ```ts
 * const isNarrow = useMediaQuery(NARROW_MEDIA)
 * ```
 *
 * 有些决定只能放在 JS 侧：「这一列要不要渲染」「侧边栏是不是折叠」——
 * 它们牵连插槽、事件绑定与组件状态，纯 CSS 媒体查询管不了。
 */
export function useMediaQuery(query: string): Ref<boolean> {
  const matches = ref(false)
  let mediaQueryList: MediaQueryList | null = null

  const update = (): void => {
    matches.value = mediaQueryList?.matches ?? false
  }

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    mediaQueryList = window.matchMedia(query)
    update()
    mediaQueryList.addEventListener('change', update)
    // 同时听 window 的 resize：有些环境（浏览器的设备模拟）改了视口宽度
    // 只重新求值媒体查询、不派发 change 事件，只听 change 会漏掉。
    window.addEventListener('resize', update)
  }

  onUnmounted(() => {
    mediaQueryList?.removeEventListener('change', update)
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', update)
    }
  })

  return matches
}
