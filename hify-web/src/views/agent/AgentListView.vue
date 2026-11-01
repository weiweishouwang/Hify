<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createAgent, deleteAgent, getAgentDetail, getAgentList, getAgentOptions, updateAgent,
} from '@/api/agent'
import type { AgentDetail, AgentOptions, AgentStatus, AgentSummary } from '@/types/agent'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'
import {
  AGENT_FORM_DEFAULTS, AGENT_STATUS_OPTIONS, agentPayload, formFromDetail, type AgentForm,
} from './agentForm'

const STATUS_TAGS: Record<AgentStatus, 'info' | 'success' | 'warning'> = {
  draft: 'info', published: 'success', disabled: 'warning',
}
const STATUS_LABELS: Record<AgentStatus, string> = {
  draft: '草稿', published: '已发布', disabled: '已停用',
}
const SOURCE_LABELS: Record<string, string> = { builtin: '内置', http: 'HTTP', mcp: 'MCP' }

const columns: HifyTableColumn<AgentSummary>[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: '主模型', slot: 'model', minWidth: 160 },
  { label: '状态', slot: 'status', width: 110 },
  { label: '工具', prop: 'toolCount', width: 80, align: 'right' },
  { label: '知识库', prop: 'knowledgeCount', width: 90, align: 'right' },
  { label: '引用', slot: 'unavailable', width: 110 },
  { label: '更新时间', slot: 'updatedAt', width: 170, hideOnNarrow: true },
  { label: '操作', slot: 'actions', width: 150, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<AgentSummary>>()
const dialogRef = ref<{ open: (data?: Partial<AgentForm>) => void; formData: AgentForm }>()
const dialogVisible = ref(false)
const original = ref<AgentDetail | null>(null)
const deletingIds = ref(new Set<string>())
const filterStatus = ref<AgentStatus | ''>('')
const filterKeyword = ref('')
const options = ref<AgentOptions>({ models: [], tools: [], knowledgeBases: [], truncated: false })
const optionsLoading = ref(false)
let disposed = false

const dialogTitle = computed(() => (original.value ? '编辑 Agent' : '新增 Agent'))
const rules: FormRules<AgentForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  modelPublicId: [{ required: true, message: '请选择主模型', trigger: 'change' }],
}

/**
 * 可选工具：把已绑定但已停用 / 已删除的补一条占位项。
 *
 * 不补的话，多选框里会出现一个光秃秃的 id，用户不知道那是什么。
 */
const toolChoices = computed(() => {
  const bound = original.value?.toolIds ?? []
  const known = new Set(options.value.tools.map((tool) => tool.publicId))
  const missing = bound
    .filter((id) => !known.has(id))
    .map((id) => ({ publicId: id, name: '（已停用或已删除）', sourceType: 'http' as const, description: id }))
  return [...options.value.tools, ...missing]
})

const knowledgeChoices = computed(() => {
  const bound = original.value?.knowledgeIds ?? []
  const known = new Set(options.value.knowledgeBases.map((kb) => kb.publicId))
  const missing = bound
    .filter((id) => !known.has(id))
    .map((id) => ({ publicId: id, name: '（已停用或已删除）', description: id, docCount: 0 }))
  return [...options.value.knowledgeBases, ...missing]
})

function fetchAgents(params: { size: number; cursor?: string }) {
  return getAgentList({
    size: params.size,
    cursor: params.cursor,
    ...(filterStatus.value ? { status: filterStatus.value } : {}),
    ...(filterKeyword.value.trim() ? { keyword: filterKeyword.value.trim() } : {}),
  })
}

const { execute: save, loading: saving } = useRequest(async (form: AgentForm) => {
  const existing = original.value
  return existing
    ? updateAgent(existing.publicId, agentPayload(form, existing))
    : createAgent(agentPayload(form))
})

async function loadOptions(): Promise<void> {
  if (optionsLoading.value) return
  optionsLoading.value = true
  try {
    const result = await getAgentOptions()
    if (!disposed) options.value = result
  } catch {
    // 失败由统一请求层提示
  } finally { optionsLoading.value = false }
}

async function openCreate(): Promise<void> {
  original.value = null
  dialogRef.value?.open()
  await loadOptions()
}

async function openEdit(row: AgentSummary): Promise<void> {
  try {
    const detail = await getAgentDetail(row.publicId)
    if (disposed) return
    original.value = detail
    dialogRef.value?.open(formFromDetail(detail))
    await loadOptions()
  } catch {
    // 失败由统一请求层提示
  }
}

async function handleSubmit(form: AgentForm): Promise<void> {
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新增 Agent' : '已保存')
  dialogVisible.value = false
  await tableRef.value?.refresh({ reset: creating })
}

async function handleDelete(row: AgentSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？`, () => deleteAgent(id), { successText: '已删除 Agent' })
    if (await confirm()) await tableRef.value?.refresh()
  } finally { deletingIds.value.delete(id) }
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const time = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('zh-CN', { hour12: false })
}

onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <div class="page">
    <PageHeader title="Agent 管理" description="配置 Agent：选模型、绑工具、设提示词，可选绑知识库。">
      <template #actions>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-button type="primary" :disabled="saving" @click="openCreate">
          <el-icon><Plus /></el-icon>新增 Agent
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="agent-filter">
        <el-select v-model="filterStatus" class="agent-filter__status" placeholder="全部状态" clearable
          @change="tableRef?.refresh({ reset: true })">
          <el-option v-for="option in AGENT_STATUS_OPTIONS" :key="option.value" :label="option.label"
            :value="option.value" />
        </el-select>
        <el-input v-model="filterKeyword" class="agent-filter__keyword" placeholder="按名称或描述搜索" clearable
          @keyup.enter="tableRef?.refresh({ reset: true })" @clear="tableRef?.refresh({ reset: true })" />
        <el-button :icon="Search" @click="tableRef?.refresh({ reset: true })">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchAgents" pagination-mode="cursor" row-key="publicId">
        <template #model="{ row }">
          <span class="mono">{{ row.modelDisplayName ?? '—' }}</span>
        </template>
        <template #status="{ row }">
          <el-tag :type="STATUS_TAGS[row.status] ?? 'info'" effect="light">
            {{ STATUS_LABELS[row.status] ?? row.status }}
          </el-tag>
        </template>
        <template #unavailable="{ row }">
          <el-tag v-if="row.unavailableCount > 0" type="danger" effect="light">
            {{ row.unavailableCount }} 项不可用
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
        <template #updatedAt="{ row }"><span class="mono">{{ formatTime(row.updatedAt) }}</span></template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" v-model:loading="saving" :title="dialogTitle"
      width="720px" label-width="110px" label-position="right" :rules="rules" :initial-values="AGENT_FORM_DEFAULTS"
      @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个 Agent 起个名字" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="一句话说明它是干什么的" />
        </el-form-item>
        <el-form-item label="主模型" prop="modelPublicId">
          <el-select v-model="form.modelPublicId" class="full-width" placeholder="选择对话模型"
            :loading="optionsLoading" filterable>
            <el-option v-for="model in options.models" :key="model.publicId"
              :label="`${model.displayName}（${model.providerName ?? '未知接入点'}）`" :value="model.publicId" />
          </el-select>
          <span class="field-hint">只有用途为「对话」且已启用的模型能选。</span>
        </el-form-item>
        <el-form-item label="系统提示词" prop="promptTemplate">
          <el-input v-model="form.promptTemplate" type="textarea" :rows="5" class="mono"
            placeholder="写给模型看的人设与规则" />
        </el-form-item>
        <el-form-item label="绑定工具" prop="toolIds">
          <el-select v-model="form.toolIds" class="full-width" multiple filterable placeholder="可不绑"
            :loading="optionsLoading">
            <el-option v-for="tool in toolChoices" :key="tool.publicId"
              :label="`${tool.name}（${SOURCE_LABELS[tool.sourceType] ?? tool.sourceType}）`" :value="tool.publicId" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定知识库" prop="knowledgeIds">
          <el-select v-model="form.knowledgeIds" class="full-width" multiple filterable placeholder="可不绑"
            :loading="optionsLoading">
            <el-option v-for="kb in knowledgeChoices" :key="kb.publicId" :label="kb.name" :value="kb.publicId" />
          </el-select>
        </el-form-item>
        <el-form-item label="采样温度" prop="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2"
            controls-position="right" class="param-input" />
        </el-form-item>
        <el-form-item label="输出上限" prop="maxTokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="200000" :step="256" controls-position="right"
            class="param-input" />
        </el-form-item>
        <el-form-item label="核采样" prop="topP">
          <el-input-number v-model="form.topP" :min="0" :max="1" :step="0.05" :precision="2" controls-position="right"
            class="param-input" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width">
            <el-option v-for="option in AGENT_STATUS_OPTIONS" :key="option.value" :label="option.label"
              :value="option.value" />
          </el-select>
          <span class="field-hint">只有「已发布」的 Agent 能被对话页选中。</span>
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.agent-filter { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-4); }
.agent-filter__status { width: 200px; }
.agent-filter__keyword { width: 240px; }
.full-width { width: 100%; }
.param-input { width: 200px; }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.muted { color: var(--color-text-tertiary); }
</style>
