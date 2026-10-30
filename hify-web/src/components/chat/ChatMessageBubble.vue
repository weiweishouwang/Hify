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
  margin: 0 auto var(--hify-space-4);
  padding: var(--hify-space-3) var(--hify-space-4);
  border-radius: var(--hify-radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
}

/* 用户气泡用主色的极浅一档，和助手气泡拉开层次 */
.bubble--user {
  background: var(--color-primary-50);
  border-color: var(--color-primary-100);
}

.bubble__role {
  margin-bottom: var(--hify-space-1);
  font-size: var(--hify-font-size-xs);
  color: var(--color-text-secondary);
}

.bubble__content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.bubble__cursor {
  margin-top: var(--hify-space-1);
  color: var(--color-text-secondary);
}
</style>
