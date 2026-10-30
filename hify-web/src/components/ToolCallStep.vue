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
  gap: 8px;
  margin: 6px 0;
  padding: 8px 12px;
  font-size: 13px;
  background: #f7f8fa;
  border: 1px solid var(--hify-border);
  border-radius: 6px;
}

.tool-call__name {
  font-weight: 500;
}

.tool-call__status {
  color: var(--hify-text-muted);
}

.tool-call__result {
  flex-basis: 100%;
  margin: 4px 0 0;
  padding: 8px;
  max-height: 200px;
  overflow: auto;
  font-size: 12px;
  background: var(--hify-surface);
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
