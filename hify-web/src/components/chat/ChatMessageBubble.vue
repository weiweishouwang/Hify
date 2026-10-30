<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { ChatMessage } from '@/types/chat'
import ToolCallStep from './ToolCallStep.vue'

const props = defineProps<{
  message: ChatMessage
}>()

const roleLabels: Record<string, string> = {
  user: '我',
  assistant: '助手',
  system: '系统',
  tool: '工具',
}

const roleLabel = computed(() => roleLabels[props.message.role] ?? props.message.role)
</script>

<template>
  <div class="bubble" :class="`bubble--${message.role}`">
    <div class="bubble__role">{{ roleLabel }}</div>
    <div class="bubble__content">{{ message.content }}</div>
    <ToolCallStep v-for="call in message.toolCalls ?? []" :key="call.id" :call="call" />
    <div v-if="message.status === 'streaming' || message.status === 'pending'" class="bubble__cursor">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>
  </div>
</template>

<style scoped>
.bubble {
  max-width: 760px;
  margin: 0 auto 16px;
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid var(--hify-border);
  background: var(--hify-surface);
}

.bubble--user {
  background: #ecf5ff;
  border-color: #d9ecff;
}

.bubble__role {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--hify-text-muted);
}

.bubble__content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.bubble__cursor {
  margin-top: 4px;
  color: var(--hify-text-muted);
}
</style>
