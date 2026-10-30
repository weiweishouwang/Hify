<script setup lang="ts">
import { computed } from 'vue'
import { Check, Close, Loading } from '@element-plus/icons-vue'
import type { ToolCall } from '@/types/chat'

const props = defineProps<{
  call: ToolCall
}>()

const icon = computed(() => {
  if (props.call.status === 'running') {
    return Loading
  }
  return props.call.status === 'error' ? Close : Check
})

const statusText = computed(() => {
  if (props.call.status === 'running') {
    return '执行中'
  }
  return props.call.status === 'error' ? '失败' : '完成'
})
</script>

<template>
  <div class="tool-call">
    <el-icon class="tool-call__icon" :class="{ 'is-loading': call.status === 'running' }">
      <component :is="icon" />
    </el-icon>
    <span class="tool-call__name">{{ call.name || '未知工具' }}</span>
    <span class="tool-call__status">{{ statusText }}</span>
    <pre v-if="call.result" class="tool-call__result">{{ call.result }}</pre>
  </div>
</template>

<style scoped>
.tool-call {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--hify-space-2);
  margin: var(--hify-space-2) 0;
  padding: var(--hify-space-2) var(--hify-space-3);
  font-size: var(--hify-font-size-sm);
  background: var(--color-bg-subtle);
  border: 1px solid var(--color-border);
  border-radius: var(--hify-radius-md);
}

.tool-call__name {
  font-weight: var(--hify-weight-medium);
}

.tool-call__status {
  color: var(--color-text-secondary);
}

.tool-call__result {
  flex-basis: 100%;
  margin: var(--hify-space-1) 0 0;
  padding: var(--hify-space-2);
  max-height: 200px;
  overflow: auto;
  font-size: var(--hify-font-size-xs);
  background: var(--color-bg-card);
  border-radius: var(--hify-radius-sm);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
