<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { createProvider, deleteProvider, getProviderDetail, getProviderList, testConnection, updateProvider } from '@/api/provider'
import type { HealthStatus, ModelConfig, ProviderConfig, ProviderListItem, ProviderType } from '@/types/provider'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'
import { providerPayload, type ProviderForm } from './providerForm'

const PROVIDER_TYPES: Array<{ value: ProviderType; label: string }> = [
  { value: 'openai', label: 'OpenAI 兼容' }, { value: 'anthropic', label: 'Anthropic' }, { value: 'ollama', label: 'Ollama' },
]
const HEALTH_TAGS: Record<HealthStatus, 'success' | 'danger' | 'warning' | 'info'> = {
  UP: 'success', DOWN: 'danger', DEGRADED: 'warning', UNKNOWN: 'info',
}
const columns: HifyTableColumn<ProviderListItem>[] = [
  { label: '名称', prop: 'name', minWidth: 160 },
  { label: '类型', prop: 'type', width: 130, formatter: row => PROVIDER_TYPES.find(type => type.value === row.type)?.label ?? '—' },
  { label: 'Base URL', slot: 'baseUrl', minWidth: 240, hideOnNarrow: true },
  { label: '状态', slot: 'status', width: 85 },
  { label: '健康状态', slot: 'health', width: 160 },
  { label: '模型数', slot: 'models', width: 90 },
  { label: '创建时间', prop: 'createdAt', width: 170, hideOnNarrow: true, formatter: row => formatTime(row.createdAt) },
  { label: '操作', slot: 'actions', width: 245, fixed: 'right' },
]
const tableRef = ref<HifyTableHandle<ProviderListItem>>()
const dialogRef = ref<{ open: (data?: Partial<ProviderForm>) => void; formData: ProviderForm }>()
const dialogVisible = ref(false)
const original = ref<ProviderConfig | null>(null)
const editingLoadingId = ref<string | null>(null)
const showApiKey = ref(false)
const testingIds = ref(new Set<string>())
const deletingIds = ref(new Set<string>())
const expandedIds = new Set<string>()
let editSequence = 0
let modelSequence = 0
let disposed = false
interface ModelState { loading: boolean; error: boolean; items?: ModelConfig[]; request: number }
const modelStates = ref<Record<string, ModelState>>({})
const dialogTitle = computed(() => original.value ? '编辑模型提供商' : '新增模型提供商')
const baseUrlHint = 'https://api.openai.com/v1'
const formDefaults = {
  name: '', type: 'openai' as ProviderType, apiKey: '', baseUrl: '',
  anthropicVersion: '2023-06-01', enabled: true,
} satisfies Partial<ProviderForm>
const rules: FormRules<ProviderForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [{ required: true, message: 'Base URL 不能为空', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: 'Base URL 要以 http:// 或 https:// 开头', trigger: 'blur' }],
  apiKey: [{ validator: (_rule, value: unknown, callback) => {
    const form = dialogRef.value?.formData
    const retained = original.value?.type === 'anthropic' && original.value.credentialsConfigured
    if (form?.type === 'anthropic' && !(typeof value === 'string' && value.trim()) && !retained) {
      callback(new Error('Anthropic 必须配置 API Key'))
    } else callback()
  }, trigger: 'blur' }],
  anthropicVersion: [{ required: true, pattern: /^\d{4}-\d{2}-\d{2}$/, message: '版本采用 yyyy-MM-dd 格式', trigger: 'blur' }],
}

const { execute: save, loading: saving } = useRequest(async (form: ProviderForm) => {
  const existing = original.value
  return existing ? updateProvider(existing.publicId, providerPayload(form, existing)) : createProvider(providerPayload(form))
})

function openCreate(): void {
  editSequence += 1
  editingLoadingId.value = null
  original.value = null
  showApiKey.value = false
  dialogRef.value?.open()
}

async function openEdit(row: ProviderListItem): Promise<void> {
  if (!row.publicId) return
  const sequence = ++editSequence
  editingLoadingId.value = row.publicId
  try {
    const detail = await getProviderDetail(row.publicId)
    if (disposed || sequence !== editSequence) return
    original.value = detail.provider
    showApiKey.value = false
    dialogRef.value?.open({ name: detail.provider.name, type: detail.provider.type, apiKey: '',
      baseUrl: detail.provider.baseUrl, enabled: detail.provider.enabled,
      anthropicVersion: detail.provider.authConfig.anthropicVersion ?? '2023-06-01' })
  } catch {
    // 请求拦截器已经提示，保留当前页面。
  } finally {
    if (sequence === editSequence) editingLoadingId.value = null
  }
}

async function handleSubmit(form: ProviderForm): Promise<void> {
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新增模型提供商' : '已保存')
  dialogVisible.value = false
  await refreshTable(creating)
}

async function refreshTable(reset = false): Promise<void> {
  modelStates.value = {}
  await tableRef.value?.refresh({ reset })
}

async function handleDelete(row: ProviderListItem): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？存在模型时需要先删除模型。`, () => deleteProvider(id), { successText: '已删除提供商' })
    if (await confirm()) {
      expandedIds.delete(id)
      await refreshTable()
    }
  } finally { deletingIds.value.delete(id) }
}

async function handleTest(row: ProviderListItem): Promise<void> {
  if (!row.publicId || testingIds.value.has(row.publicId)) return
  testingIds.value.add(row.publicId)
  try {
    const result = await testConnection(row.publicId)
    if (!disposed) {
      if (result.connected) ElMessage.success(`连接成功，耗时 ${result.latencyMs} ms`)
      else ElMessage.warning('连通性测试未通过')
      await refreshTable()
    }
  } catch {
    // 失败由统一请求层 ElMessage 提示，不能重复弹出。
  } finally { testingIds.value.delete(row.publicId) }
}

function state(row: ProviderListItem): ModelState | undefined { return modelStates.value[row.publicId] }
async function loadModels(row: ProviderListItem, force = false): Promise<void> {
  if (!row.publicId) return
  const current = state(row)
  if (current?.loading || (!force && current?.items)) return
  const request = ++modelSequence
  modelStates.value[row.publicId] = { loading: true, error: false, request }
  try {
    const result = await getProviderDetail(row.publicId)
    if (!disposed && state(row)?.request === request) {
      modelStates.value[row.publicId] = { loading: false, error: false, items: result.provider.modelConfigs, request }
    }
  } catch {
    if (!disposed && state(row)?.request === request) modelStates.value[row.publicId] = { loading: false, error: true, request }
  }
}

function handleExpandChange(row: ProviderListItem, expanded: boolean): void {
  if (expanded) { expandedIds.add(row.publicId); void loadModels(row) }
  else expandedIds.delete(row.publicId)
}

function handleLoaded(rows: ProviderListItem[]): void {
  const visible = new Set(rows.map(row => row.publicId))
  for (const id of expandedIds) if (!visible.has(id)) expandedIds.delete(id)
  for (const id of Object.keys(modelStates.value)) if (!visible.has(id)) delete modelStates.value[id]
  for (const row of rows) if (expandedIds.has(row.publicId)) void loadModels(row)
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const time = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('zh-CN', { hour12: false })
}

onBeforeUnmount(() => { disposed = true; editSequence += 1 })
</script>

<template>
  <div class="page">
    <PageHeader title="模型提供商管理" description="统一管理 OpenAI 兼容端点、Anthropic 与 Ollama，查看健康状态和关联模型。">
      <template #actions>
        <el-button :icon="Refresh" @click="refreshTable()">刷新</el-button>
        <el-button type="primary" :disabled="saving" @click="openCreate">
          <el-icon><Plus /></el-icon>新增提供商
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <HifyTable ref="tableRef" :columns="columns" :api="getProviderList" pagination-mode="cursor" row-key="publicId"
        @expand-change="handleExpandChange" @loaded="handleLoaded">
        <template #baseUrl="{ row }"><span class="mono">{{ row.baseUrl }}</span></template>
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
        </template>
        <template #health="{ row }">
          <div class="health-cell">
            <el-tag :type="HEALTH_TAGS[row.providerHealth?.status] ?? 'info'" :title="row.providerHealth?.errorMessage ?? ''" effect="light">
              {{ row.providerHealth?.status ?? 'UNKNOWN' }}
            </el-tag>
            <span class="latency mono">{{ row.providerHealth?.latencyMs != null ? `${row.providerHealth.latencyMs} ms` : '—' }}</span>
          </div>
        </template>
        <template #models="{ row }">
          <el-button text type="primary" :data-testid="`models-${row.publicId}`" title="已启用模型数，点击展开全部模型配置"
            @click="tableRef?.toggleRowExpansion(row)">{{ row.enabledModelCount ?? 0 }}</el-button>
        </template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :loading="editingLoadingId === row.publicId" :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--test" :data-testid="`test-${row.publicId}`"
            :loading="testingIds.has(row.publicId)" @click="handleTest(row)">连通性测试</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
        <template #expand="{ row }">
          <div v-loading="state(row)?.loading" class="model-panel">
            <p class="model-caption">关联模型配置（模型数仅统计已启用项）</p>
            <div v-if="state(row)?.error" class="model-error" role="alert">
              <span>模型加载失败</span><el-button text type="primary" @click="loadModels(row, true)">重新加载</el-button>
            </div>
            <el-table v-else-if="state(row)?.items?.length" :data="state(row)?.items" row-key="publicId" size="small">
              <el-table-column prop="displayName" label="模型名称" min-width="140" />
              <el-table-column prop="modelName" label="模型标识" min-width="180" />
              <el-table-column prop="modelType" label="用途" width="110" />
              <el-table-column label="状态" width="100"><template #default="scope">
                <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '禁用' }}</el-tag>
              </template></el-table-column>
            </el-table>
            <el-empty v-else :description="state(row)?.loading ? '模型加载中' : '暂无关联模型'" :image-size="48" />
          </div>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" v-model:loading="saving" :title="dialogTitle"
      width="520px" label-width="100px" label-position="right" :rules="rules" :initial-values="formDefaults" @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name"><el-input v-model="form.name" placeholder="给这个接入点起个名字" /></el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" class="full-width" @change="form.apiKey = ''">
            <el-option v-for="type in PROVIDER_TYPES" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type !== 'ollama'" label="API Key" prop="apiKey">
          <div class="field-stack">
            <el-input v-model="form.apiKey" :type="showApiKey ? 'text' : 'password'" autocomplete="new-password"
              :placeholder="original?.credentialsConfigured && original.type === form.type ? '留空保留已保存密钥' : '上游服务的访问密钥'">
              <template #suffix><button type="button" class="field-toggle" @click="showApiKey = !showApiKey">{{ showApiKey ? '隐藏' : '显示' }}</button></template>
            </el-input>
            <span class="field-hint">{{ form.type === 'anthropic' ? 'Anthropic 必须配置密钥；已有密钥不会回传。' : '自托管的无鉴权兼容端点可留空。' }}</span>
          </div>
        </el-form-item>
        <el-form-item v-if="form.type === 'anthropic'" label="API 版本" prop="anthropicVersion"><el-input v-model="form.anthropicVersion" /></el-form-item>
        <el-form-item label="Base URL" prop="baseUrl"><el-input v-model="form.baseUrl" :placeholder="baseUrlHint" /></el-form-item>
        <el-form-item label="启用" prop="enabled"><el-switch v-model="form.enabled" /></el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit, .action-btn--test { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.health-cell { display: flex; align-items: center; gap: var(--hify-space-2); }
.latency, .field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.model-panel { padding: var(--hify-space-4) var(--hify-space-6); }
.model-caption { color: var(--color-text-secondary); margin-bottom: var(--hify-space-3); }
.model-error { display: flex; align-items: center; gap: var(--hify-space-3); color: var(--color-danger-600); }
.full-width, .field-stack { width: 100%; }
.field-stack { display: flex; flex-direction: column; gap: var(--hify-space-1); }
.field-toggle { background: transparent; border: none; color: var(--color-text-secondary); font-size: var(--hify-font-size-xs); cursor: pointer; padding: 0 var(--hify-space-1); }
.field-toggle:hover { color: var(--color-primary-600); }
</style>
