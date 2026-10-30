<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { streamChat } from '@/api/chat'
import { useConnectionStore } from '@/stores/connection'
import { useConversationStore } from '@/stores/conversation'
import { newLocalId } from '@/utils/id'
import ChatMessageBubble from '@/components/chat/ChatMessageBubble.vue'

/**
 * 流式对话面板。
 *
 * 实现已经完成（SSE 客户端 + 消息气泡 + 工具调用中间态 + 连接状态），
 * 但后端的对话接口 `POST /api/v1/conversations/{id}/messages` 还没实现，
 * 所以 `views/chat/ChatView.vue` 目前只是一个占位页，没挂这个组件。
 *
 * 后端接口就绪后，把 ChatView 换成 `<ChatPanel />` 即可。
 */
const conversation = useConversationStore()
const connection = useConnectionStore()

const input = ref('')
const sending = ref(false)
const listRef = ref<HTMLElement | null>(null)

const canSend = computed(() => input.value.trim().length > 0 && !sending.value)

/**
 * 会话 id。
 *
 * 后端的会话创建接口（POST /api/v1/conversations）还没实现，
 * 这里先用浏览器侧生成的 id 占位；接口就绪后换成后端返回的 id 即可。
 */
function ensureSession(): string {
  const current = conversation.sessionId
  if (!current) {
    const created = newLocalId()
    conversation.setSession(created)
    return created
  }
  return current
}

async function send(): Promise<void> {
  const content = input.value.trim()
  if (!content || sending.value) {
    return
  }
  // 先拿会话 id：setSession 会清空消息列表，必须在 append 之前
  const sessionId = ensureSession()

  input.value = ''
  sending.value = true

  conversation.append({ role: 'user', content, status: 'done' })
  const reply = conversation.append({ role: 'assistant', content: '', status: 'pending' })

  const signal = connection.begin()
  try {
    await streamChat(
      sessionId,
      content,
      {
        onOpen: () => connection.markOpen(),
        onText: (delta) => conversation.appendDelta(reply.id, delta),
        onToolCall: (call) => conversation.upsertToolCall(call.id, call),
        onToolResult: (call) => conversation.upsertToolCall(call.id, call),
        onDone: () => conversation.setStatus(reply.id, 'done'),
        onError: (error) => {
          conversation.setStatus(reply.id, 'error')
          connection.markError(error.message)
          ElMessage.error(error.message)
        },
        onClose: () => connection.markClosed(),
      },
      signal,
    )
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function stop(): void {
  connection.abort()
  sending.value = false
}

function scrollToBottom(): void {
  const element = listRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

onBeforeUnmount(() => {
  // 页面卸载必须断流，否则 SSE 连接泄漏（CLAUDE.md §2.5）
  connection.abort()
})
</script>

<template>
  <div class="chat">
    <div ref="listRef" class="chat__list">
      <el-empty
        v-if="conversation.messages.length === 0"
        description="发一条消息开始对话。流式输出走 SSE（fetch + ReadableStream），不是原生 EventSource。"
      />
      <ChatMessageBubble
        v-for="message in conversation.messages"
        :key="message.id"
        :message="message"
      />
    </div>

    <div class="chat__composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入内容，Ctrl/⌘ + Enter 发送"
        @keydown.ctrl.enter="send"
        @keydown.meta.enter="send"
      />
      <div class="chat__actions">
        <el-button v-if="sending" @click="stop">停止</el-button>
        <el-button v-else type="primary" :disabled="!canSend" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.chat__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0 16px;
}

.chat__composer {
  padding: var(--hify-space-3) var(--hify-space-4);
  background: var(--hify-bg-surface);
  border: 1px solid var(--hify-border);
  border-radius: var(--hify-radius-lg);
}

.chat__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--hify-space-2);
}
</style>
