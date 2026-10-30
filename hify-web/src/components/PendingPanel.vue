<script setup lang="ts">
defineProps<{
  title: string
  description: string
  hint?: string
}>()
</script>

<template>
  <el-card shadow="never" class="pending">
    <template #header>
      <div class="pending__header">
        <span class="pending__title">{{ title }}</span>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </div>
    </template>
    <el-empty :description="description">
      <p v-if="hint" class="pending__hint">{{ hint }}</p>
    </el-empty>
  </el-card>
</template>

<style scoped>
.pending__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pending__title {
  font-size: var(--hify-text-lg);
  font-weight: var(--hify-weight-semibold);
}

.pending__hint {
  max-width: 520px;
  margin: 0 auto;
  color: var(--hify-text-secondary);
  font-size: var(--hify-text-sm);
  line-height: 1.7;
}
</style>
