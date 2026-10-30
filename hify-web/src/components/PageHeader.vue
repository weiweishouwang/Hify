<script setup lang="ts">
/**
 * 页面标题区。
 *
 * 每个页面顶部统一是「标题 + 描述文字 + 右侧操作区」——
 * 标题说清这是哪一页，描述说清这页能干什么，操作区放这一页的动作。
 * 页面自己不要另写一份标题，位置与间距统一由这里定。
 */
defineProps<{
  /** 页面标题。 */
  title: string
  /** 一句话说明这页是干什么的。 */
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header__text">
      <h2 class="page-header__title">{{ title }}</h2>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>

    <!-- 操作区靠右；没有操作时不占位 -->
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--hify-element-gap);
}

.page-header__title {
  font-size: var(--hify-font-size-xl);
  font-weight: var(--hify-weight-semibold);
  line-height: var(--hify-leading-tight);
  color: var(--color-text-primary);
}

.page-header__desc {
  margin-top: var(--hify-space-1);
  font-size: var(--hify-font-size-sm);
  color: var(--color-text-secondary);
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: var(--hify-space-2);
  flex-shrink: 0;
}
</style>
