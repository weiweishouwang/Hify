<script setup lang="ts">
/**
 * 未实现功能的空状态。
 *
 * 只负责"这块还没做"这件事的视觉表达 —— 标题由页面的 PageHeader 承担，
 * 这里只留空状态与一句说明。
 */
defineProps<{
  /** 空状态描述。 */
  description: string
  /** 补充说明：这块将来怎么实现、归哪个模块。 */
  hint?: string
}>()
</script>

<template>
  <div class="page-card pending">
    <el-empty :description="description">
      <p v-if="hint" class="pending__hint">{{ hint }}</p>
    </el-empty>
  </div>
</template>

<style scoped>
.pending {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.pending__hint {
  max-width: 520px;
  margin: 0 auto;
  color: var(--color-text-secondary);
  font-size: var(--hify-font-size-sm);
  line-height: 1.7;
}
</style>
