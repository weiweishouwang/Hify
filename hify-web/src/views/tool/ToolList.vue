<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { createTool, deleteTool, getToolDetail, getToolList, syncTools, testTool, updateTool } from '@/api/tool'
import type { ToolDetail, ToolInvokeResult, ToolMethod, ToolSourceType, ToolSummary } from '@/types/tool'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'
import { formFromDetail, SCHEMA_EXAMPLE, toolPayload, type HeaderRow, type ToolForm } from './toolForm'

const SOURCE_OPTIONS: Array<{ value: ToolSourceType; label: string }> = [
  { value: 'builtin', label: '内置' },
  { value: 'http', label: 'HTTP' },
  { value: 'mcp', label: 'MCP' },
]
const SOURCE_TAGS: Record<ToolSourceType, 'primary' | 'success' | 'warning'> = {
  builtin: 'primary', http: 'success', mcp: 'warning',
}
const METHOD_OPTIONS: Array<{ value: ToolMethod; label: string }> = [
  { value: 'POST', label: 'POST' },
  { value: 'GET', label: 'GET' },
]

const columns: HifyTableColumn<ToolSummary>[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: '来源', slot: 'source', width: 90 },
  { label: '工具标识', slot: 'toolKey', minWidth: 150 },
  { label: '描述', prop: 'description', minWidth: 200, hideOnNarrow: true, formatter: row => row.description || '—' },
  { label: '状态', slot: 'status', width: 85 },
  { label: '操作', slot: 'actions', width: 210, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<ToolSummary>>()
const dialogRef = ref<{ open: (data?: Partial<ToolForm>) => void; formData: ToolForm }>()
const dialogVisible = ref(false)
const original = ref<ToolDetail | null>(null)
const editingLoadingId = ref<string | null>(null)
const deletingIds = ref(new Set<string>())
const syncing = ref(false)
const filterSource = ref<ToolSourceType | ''>('')
const filterKeyword = ref('')
const testVisible = ref(false)
const testTarget = ref<ToolSummary | null>(null)
const testArgsText = ref('{}')
const testRunning = ref(false)
const testResult = ref<ToolInvokeResult | null>(null)
let editSequence = 0
let disposed = false

const dialogTitle = computed(() => (original.value ? '编辑工具' : '新增工具'))
const urlHint = 'https://api.example.com/tool'
const schemaHint = SCHEMA_EXAMPLE
const formDefaults = {
  name: '', toolKey: '', description: '', url: '', method: 'POST' as ToolMethod,
  schemaText: '', headers: [] as HeaderRow[], timeoutMs: 30000, enabled: true,
} satisfies Partial<ToolForm>

const rules: FormRules<ToolForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  toolKey: [
    { required: true, message: '工具标识不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '只能包含字母、数字、下划线和连字符', trigger: 'blur' },
  ],
  url: [
    { required: true, message: '请求地址不能为空', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: '请求地址要以 http:// 或 https:// 开头', trigger: 'blur' },
  ],
  schemaText: [{ validator: (_rule, value: unknown, callback) => {
    const text = typeof value === 'string' ? value.trim() : ''
    if (!text) { callback(); return }
    try {
      const parsed: unknown = JSON.parse(text)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        callback(new Error('入参定义要是一个 JSON 对象'))
        return
      }
    } catch {
      callback(new Error('入参定义不是合法的 JSON'))
      return
    }
    callback()
  }, trigger: 'blur' }],
}

/** 表格的取数函数：把筛选条件并进游标分页参数。 */
function fetchTools(params: { size: number; cursor?: string }) {
  return getToolList({
    size: params.size,
    cursor: params.cursor,
    ...(filterSource.value ? { sourceType: filterSource.value } : {}),
    ...(filterKeyword.value.trim() ? { keyword: filterKeyword.value.trim() } : {}),
  })
}

const { execute: save, loading: saving } = useRequest(async (form: ToolForm) => {
  const existing = original.value
  return existing
    ? updateTool(existing.publicId, toolPayload(form, existing))
    : createTool(toolPayload(form))
})

function openCreate(): void {
  editSequence += 1
  editingLoadingId.value = null
  original.value = null
  dialogRef.value?.open()
}

async function openEdit(row: ToolSummary): Promise<void> {
  if (!row.publicId) return
  const sequence = ++editSequence
  editingLoadingId.value = row.publicId
  try {
    const detail = await getToolDetail(row.publicId)
    if (disposed || sequence !== editSequence) return
    original.value = detail
    dialogRef.value?.open(formFromDetail(detail))
  } catch {
    // 请求拦截器已经提示，保留当前页面。
  } finally {
    if (sequence === editSequence) editingLoadingId.value = null
  }
}

async function handleSubmit(form: ToolForm): Promise<void> {
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新增工具' : '已保存')
  dialogVisible.value = false
  await tableRef.value?.refresh({ reset: creating })
}

async function handleDelete(row: ToolSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？`, () => deleteTool(id), { successText: '已删除工具' })
    if (await confirm()) await tableRef.value?.refresh()
  } finally { deletingIds.value.delete(id) }
}

async function handleSync(): Promise<void> {
  if (syncing.value) return
  syncing.value = true
  try {
    const result = await syncTools()
    if (disposed) return
    ElMessage.success(`目录已同步：发现 ${result.discovered}，新增 ${result.created}，更新 ${result.updated}，下架 ${result.disabled}`)
    await tableRef.value?.refresh()
  } catch {
    // 失败由统一请求层提示。
  } finally { syncing.value = false }
}

function openTest(row: ToolSummary): void {
  // 固定列会被 Element Plus 渲染第二份副本，副本里的行是空对象 —— 直接忽略
  if (!row.publicId) return
  testTarget.value = row
  testArgsText.value = '{}'
  testResult.value = null
  testVisible.value = true
}

async function runTest(): Promise<void> {
  const target = testTarget.value
  if (!target?.publicId || testRunning.value) return
  let args: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(testArgsText.value.trim() || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('not an object')
    args = parsed as Record<string, unknown>
  } catch {
    ElMessage.error('入参要是一个 JSON 对象')
    return
  }
  testRunning.value = true
  try {
    const result = await testTool(target.publicId, args)
    if (!disposed) testResult.value = result
  } catch {
    // 失败由统一请求层提示，保留上一次结果。
  } finally { testRunning.value = false }
}

function addHeaderRow(): void {
  const form = dialogRef.value?.formData
  if (!form) return
  form.headers = [...(form.headers ?? []), { name: '', value: '' }]
}

function removeHeaderRow(index: number): void {
  const form = dialogRef.value?.formData
  if (!form?.headers) return
  form.headers = form.headers.filter((_row, position) => position !== index)
}

function isHttp(row: ToolSummary): boolean {
  return row.sourceType === 'http'
}

function sourceLabel(source: ToolSourceType): string {
  return SOURCE_OPTIONS.find(option => option.value === source)?.label ?? source
}

onBeforeUnmount(() => { disposed = true; editSequence += 1 })
</script>

<template>
  <div class="page">
    <PageHeader title="工具管理" description="统一管理内置工具、自定义 HTTP 工具与 MCP 工具，这里是 Agent 可以绑定的工具目录。">
      <template #actions>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-button :loading="syncing" @click="handleSync">同步目录</el-button>
        <el-button type="primary" :disabled="saving" @click="openCreate">
          <el-icon><Plus /></el-icon>新增工具
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="tool-filter">
        <el-select v-model="filterSource" class="tool-filter__source" placeholder="全部来源" clearable
          @change="tableRef?.refresh({ reset: true })">
          <el-option v-for="option in SOURCE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="filterKeyword" class="tool-filter__keyword" placeholder="按名称或工具标识搜索" clearable
          @keyup.enter="tableRef?.refresh({ reset: true })" @clear="tableRef?.refresh({ reset: true })" />
        <el-button :icon="Search" @click="tableRef?.refresh({ reset: true })">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchTools" pagination-mode="cursor" row-key="publicId">
        <template #source="{ row }">
          <el-tag :type="SOURCE_TAGS[row.sourceType] ?? 'info'" effect="light">{{ sourceLabel(row.sourceType) }}</el-tag>
        </template>
        <template #toolKey="{ row }"><span class="mono">{{ row.toolKey }}</span></template>
        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
        <template #actions="{ row }">
          <el-button v-if="isHttp(row)" text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :loading="editingLoadingId === row.publicId" :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--test" :data-testid="`test-${row.publicId}`"
            @click="openTest(row)">测试调用</el-button>
          <el-button v-if="isHttp(row)" text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" v-model:loading="saving" :title="dialogTitle"
      width="640px" label-width="110px" label-position="right" :rules="rules" :initial-values="formDefaults" @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个工具起个名字，模型看到的就是它" />
        </el-form-item>
        <el-form-item label="工具标识" prop="toolKey">
          <el-input v-model="form.toolKey" :disabled="!!original" placeholder="同来源内唯一，例如 weather_query" />
          <span v-if="original" class="field-hint">工具标识创建后不能修改。</span>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="告诉模型这个工具做什么" />
        </el-form-item>
        <el-form-item label="请求地址" prop="url">
          <el-input v-model="form.url" :placeholder="urlHint" />
          <span class="field-hint">参数放在入参里，不要写进地址的查询串。</span>
        </el-form-item>
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="form.method" class="full-width">
            <el-option v-for="option in METHOD_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时(毫秒)" prop="timeoutMs">
          <el-input-number v-model="form.timeoutMs" :min="1000" :max="120000" :step="1000" />
        </el-form-item>
        <el-form-item label="入参定义" prop="schemaText">
          <el-input v-model="form.schemaText" type="textarea" :rows="6" class="mono" :placeholder="schemaHint" />
          <span class="field-hint">JSON Schema，只用于告诉模型该传什么参数。</span>
        </el-form-item>
        <el-form-item label="请求头">
          <div class="header-editor">
            <div v-for="(row, index) in form.headers ?? []" :key="index" class="header-editor__row">
              <el-input v-model="row.name" placeholder="名称，例如 Authorization" />
              <el-input v-model="row.value" placeholder="值" />
              <el-button text class="action-btn action-btn--delete" @click="removeHeaderRow(index)">移除</el-button>
            </div>
            <el-button text type="primary" @click="addHeaderRow">添加请求头</el-button>
            <span class="field-hint">含 token 的请求头会加密存储，编辑时显示为已配置，清空即删除。</span>
          </div>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </template>
    </HifyFormDialog>

    <el-dialog v-model="testVisible" :title="`测试调用：${testTarget?.name ?? ''}`" width="620px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="入参(JSON)">
          <el-input v-model="testArgsText" type="textarea" :rows="6" class="mono" placeholder='{"city":"杭州"}' />
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="test-result">
        <div class="test-result__head">
          <el-tag :type="testResult.success ? 'success' : 'danger'" effect="light">
            {{ testResult.success ? '执行成功' : '执行失败' }}
          </el-tag>
          <span v-if="testResult.durationMs != null" class="field-hint">{{ testResult.durationMs }} ms</span>
        </div>
        <pre v-if="testResult.content" class="test-result__body">{{ testResult.content }}</pre>
        <p v-else-if="testResult.errorMessage" class="test-result__error">{{ testResult.errorMessage }}</p>
      </div>
      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testRunning" @click="runTest">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit, .action-btn--test { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.tool-filter { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-4); }
.tool-filter__source { width: 140px; }
.tool-filter__keyword { width: 260px; }
.full-width { width: 100%; }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.header-editor { display: flex; flex-direction: column; gap: var(--hify-space-2); width: 100%; }
.header-editor__row { display: flex; align-items: center; gap: var(--hify-space-2); }
.header-editor__row .el-input { flex: 1; }
.test-result { border-top: 1px solid var(--color-border); padding-top: var(--hify-space-3); }
.test-result__head { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-2); }
.test-result__body {
  margin: 0; max-height: 260px; overflow: auto; background: var(--color-bg-secondary);
  border-radius: var(--hify-radius-xs); padding: var(--hify-space-3);
  font-family: var(--hify-font-family-mono, monospace); font-size: var(--hify-font-size-xs);
  white-space: pre-wrap; word-break: break-all;
}
.test-result__error { color: var(--color-danger-600); margin: 0; }
</style>
