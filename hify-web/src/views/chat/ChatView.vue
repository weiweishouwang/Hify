<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { Delete, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormRules } from 'element-plus'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createConversation, deleteConversation, getConversationList, getConversationMessages,
} from '@/api/conversation'
import { getAgentList } from '@/api/agent'
import { streamChat } from '@/api/chat'
import { useConnectionStore } from '@/stores/connection'
import { useConversationStore } from '@/stores/conversation'
import { notifySuccess } from '@/utils/notify'
import type { ConversationSummary } from '@/types/conversation'
import type { ChatMessage } from '@/types/chat'

interface AgentOption {
  publicId: string
  name: string
}

const sessions = ref<ConversationSummary[]>([])
const agents = ref<AgentOption[]>([])
const currentId = ref<string | null>(null)
const draft = ref('')
const sending = ref(false)
const loadingSessions = ref(false)
const messagesEl = ref<HTMLElement>()
const dialogRef = ref<{ open: (data?: Partial<CreateForm>) => void; formData: CreateForm }>()
const dialogVisible = ref(false)

const store = useConversationStore()
const connection = useConnectionStore()

const dialogTitle = '新建会话'
const createForm = ref<CreateForm>({ agentPublicId: '' })
const rules: FormRules<CreateForm> = {
  agentPublicId: [{ required: true, message: '请选择一个 Agent', trigger: 'change' }],
}
interface CreateForm extends Record<string, unknown> {
  agentPublicId: string
}

const messages = computed<ChatMessage[]>(() => store.messages)
const canSend = computed(() => draft.value.trim().length > 0 && !sending.value && currentId.value !== null)

async function loadSessions(): Promise<void> {
  loadingSessions.value = true
  try {
    const page = await getConversationList({ size: 50 })
    sessions.value = page.items
  } catch {
    // 失败由统一请求层提示
  } finally { loadingSessions.value = false }
}

async function loadAgents(): Promise<void> {
  try {
    const page = await getAgentList({ size: 100, status: 'published' })
    agents.value = page.items.map((item) => ({ publicId: item.publicId, name: item.name }))
  } catch {
    agents.value = []
  }
}

async function select(session: ConversationSummary): Promise<void> {
  currentId.value = session.publicId
  store.setSession(session.publicId)
  try {
    const history = await getConversationMessages(session.publicId)
    history.forEach((item) => {
      store.append({
        role: item.role,
        content: item.content,
        status: 'done',
        ...(item.toolCalls?.length ? { toolCalls: item.toolCalls.map((call) => ({ ...call, status: 'done' })) } : {}),
      })
    })
  } catch {
    // 失败由统一请求层提示
  }
  await scrollToBottom()
}

async function openCreate(): Promise<void> {
  createForm.value = { agentPublicId: '' }
  dialogRef.value?.open()
  await loadAgents()
}

async function handleCreate(form: CreateForm): Promise<void> {
  const created = await createConversation({ agentPublicId: form.agentPublicId })
  dialogVisible.value = false
  notifySuccess('已新建会话')
  await loadSessions()
  await select(created)
}

async function remove(session: ConversationSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除会话“${session.title || '新会话'}”吗？`, '删除会话', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await deleteConversation(session.publicId)
  notifySuccess('已删除会话')
  if (currentId.value === session.publicId) {
    currentId.value = null
    store.clear()
  }
  await loadSessions()
}

async function send(): Promise<void> {
  const content = draft.value.trim()
  if (!content || sending.value || !currentId.value) {
    return
  }
  const sessionId = currentId.value
  draft.value = ''
  sending.value = true

  store.append({ role: 'user', content, status: 'done' })
  const assistant = store.append({ role: 'assistant', content: '', status: 'streaming' })
  let assistantId = assistant.id
  await scrollToBottom()

  const signal = connection.begin()
  try {
    await streamChat(sessionId, content, {
      onTurn: (index) => {
        if (index > 0) {
          const next = store.append({ role: 'assistant', content: '', status: 'streaming' })
          assistantId = next.id
        }
      },
      onText: (delta) => {
        store.appendDelta(assistantId, delta)
        void scrollToBottom()
      },
      onToolCall: (call) => {
        store.upsertToolCall(call.id, call)
      },
      onToolResult: (call) => {
        store.upsertToolCall(call.id, call)
      },
      onDone: () => {
        store.setStatus(assistantId, 'done')
      },
      onError: (error) => {
        store.setStatus(assistantId, 'error')
        ElMessage.error(error.message)
      },
      onOpen: () => connection.markOpen(),
      onClose: () => connection.markClosed(),
    }, signal)
    store.setStatus(assistantId, 'done')
  } catch {
    store.setStatus(assistantId, 'error')
  } finally {
    sending.value = false
    await loadSessions()
    await selectById(sessionId)
  }
}

/** 重新从服务端拉一次消息，让界面与落库结果一致。 */
async function selectById(sessionId: string): Promise<void> {
  const session = sessions.value.find((item) => item.publicId === sessionId)
  if (session) {
    await select(session)
  }
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const element = messagesEl.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

onMounted(async () => {
  await loadSessions()
  await loadAgents()
  if (sessions.value.length > 0) {
    await select(sessions.value[0])
  }
})

onBeforeUnmount(() => {
  connection.abort()
})
</script>

<template>
  <div class="page">
    <PageHeader title="对话" description="与 Agent 多轮对话，流式输出，支持工具调用与知识库检索。">
      <template #actions>
        <el-button :icon="Refresh" :loading="loadingSessions" @click="loadSessions">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建会话</el-button>
      </template>
    </PageHeader>

    <div class="chat">
      <aside class="chat__sessions page-card">
        <div class="chat__sessions-title">会话</div>
        <div
          v-for="session in sessions"
          :key="session.publicId"
          class="chat__session"
          :class="{ 'is-active': session.publicId === currentId }"
          :data-testid="`session-${session.publicId}`"
          @click="select(session)"
        >
          <div class="chat__session-main">
            <div class="chat__session-name">{{ session.title || '新会话' }}</div>
            <div class="chat__session-meta">{{ session.agentName ?? '未绑定 Agent' }} · {{ session.messageCount }} 条</div>
          </div>
          <el-button text class="chat__session-delete" :icon="Delete" :data-testid="`delete-${session.publicId}`"
            @click.stop="remove(session)" />
        </div>
        <el-empty v-if="sessions.length === 0" description="还没有会话，点右上角新建" :image-size="60" />
      </aside>

      <section class="chat__main page-card">
        <div ref="messagesEl" class="chat__messages">
          <div v-if="messages.length === 0" class="chat__empty">
            <el-empty description="选一个会话开始对话" />
          </div>
          <div v-for="message in messages" :key="message.id" class="chat__row" :class="`is-${message.role}`">
            <div class="chat__bubble">
              <div v-for="call in message.toolCalls ?? []" :key="call.id" class="chat__tool" :class="{ 'is-error': call.error }">
                <div class="chat__tool-name">{{ call.name }}</div>
                <div v-if="call.arguments" class="chat__tool-args mono">{{ call.arguments }}</div>
                <div v-if="call.error" class="chat__tool-error">{{ call.error }}</div>
                <div v-else-if="call.result" class="chat__tool-result mono">{{ call.result }}</div>
              </div>
              <div v-if="message.content" class="chat__content">{{ message.content }}</div>
              <div v-if="message.status === 'streaming' && !message.content" class="chat__typing">正在思考…</div>
              <div v-if="message.status === 'error'" class="chat__error">这一轮出错了</div>
            </div>
          </div>
        </div>

        <div class="chat__composer">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            :disabled="!currentId"
            placeholder="说点什么…（Enter 发送，Shift+Enter 换行）"
            @keydown.enter.exact.prevent="send"
          />
          <div class="chat__composer-actions">
            <span class="chat__hint">{{ currentId ? '' : '先新建或选择一个会话' }}</span>
            <el-button type="primary" :loading="sending" :disabled="!canSend" data-testid="send"
              @click="send">发送</el-button>
          </div>
        </div>
      </section>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" :title="dialogTitle" width="480px"
      label-width="90px" :rules="rules" :initial-values="createForm" @submit="handleCreate">
      <template #default="{ form }">
        <el-form-item label="Agent" prop="agentPublicId">
          <el-select v-model="form.agentPublicId" class="full-width" placeholder="选择要对话的 Agent"
            filterable :loading="agents.length === 0">
            <el-option v-for="agent in agents" :key="agent.publicId" :label="agent.name" :value="agent.publicId" />
          </el-select>
          <span class="field-hint">只有「已发布」的 Agent 能被选中。</span>
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.chat { display: grid; grid-template-columns: 260px 1fr; gap: var(--hify-space-4); align-items: start; }
.chat__sessions { padding: var(--hify-space-4); max-height: calc(100vh - 180px); overflow: auto; }
.chat__sessions-title { font-weight: 600; margin-bottom: var(--hify-space-3); }
.chat__session { display: flex; align-items: center; gap: var(--hify-space-2); padding: var(--hify-space-2);
  border-radius: var(--hify-radius-sm); cursor: pointer; }
.chat__session:hover { background: var(--color-bg-subtle); }
.chat__session.is-active { background: var(--color-primary-50); }
.chat__session-main { flex: 1; min-width: 0; }
.chat__session-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat__session-meta { font-size: var(--hify-font-size-xs); color: var(--color-text-tertiary); }
.chat__session-delete { color: var(--color-danger-500); }
.chat__main { display: flex; flex-direction: column; min-height: calc(100vh - 180px); padding: var(--hify-space-4); }
.chat__messages { flex: 1; overflow: auto; display: flex; flex-direction: column; gap: var(--hify-space-3); }
.chat__empty { margin: auto; }
.chat__row { display: flex; }
.chat__row.is-user { justify-content: flex-end; }
.chat__bubble { max-width: 72%; padding: var(--hify-space-3) var(--hify-space-4);
  border-radius: var(--hify-radius-md); background: var(--color-bg-subtle); }
.chat__row.is-user .chat__bubble { background: var(--color-primary-600); color: #fff; }
.chat__content { white-space: pre-wrap; word-break: break-word; }
.chat__typing { color: var(--color-text-tertiary); font-size: var(--hify-font-size-sm); }
.chat__error { color: var(--color-danger-500); font-size: var(--hify-font-size-sm); margin-top: var(--hify-space-1); }
.chat__tool { border: 1px solid var(--color-border); border-radius: var(--hify-radius-sm);
  padding: var(--hify-space-2); margin-bottom: var(--hify-space-2); font-size: var(--hify-font-size-sm); }
.chat__tool.is-error { border-color: var(--color-danger-500); }
.chat__tool-name { font-weight: 600; }
.chat__tool-args, .chat__tool-result { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs);
  word-break: break-all; margin-top: var(--hify-space-1); }
.chat__tool-error { color: var(--color-danger-500); font-size: var(--hify-font-size-xs); margin-top: var(--hify-space-1); }
.chat__composer { margin-top: var(--hify-space-4); }
.chat__composer-actions { display: flex; align-items: center; justify-content: flex-end; gap: var(--hify-space-3);
  margin-top: var(--hify-space-2); }
.chat__hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.full-width { width: 100%; }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
</style>
