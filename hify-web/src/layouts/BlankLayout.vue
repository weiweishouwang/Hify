<template>
  <div class="blank-layout">
    <slot />
  </div>
</template>

<style scoped>
/**
 * 独立页面用的全屏容器：登录页、404 页。
 *
 * 这类页面不要侧边栏与顶栏，所以在 App.vue 里用 meta.hideLayout 标记，
 * App.vue 只渲染内容区，且去掉内边距，由本组件负责居中。
 */
.blank-layout {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
}
</style>
