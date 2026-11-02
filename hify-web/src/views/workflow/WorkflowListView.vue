<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createWorkflow, deleteWorkflow, getWorkflowDetail, getWorkflowList, runWorkflow, updateWorkflow,
} from '@/api/workflow'
import type { WorkflowDetail, WorkflowRunResult, WorkflowSummary } from '@/types/workflow'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'
import {
  WORKFLOW_FORM_DEFAULTS, WORKFLOW_SAMPLE, WORKFLOW_STATUS_OPTIONS, formatDefinition,
  validateDefinition, workflowPayload, workflowUpdatePayload, type WorkflowForm,
} from './workflowForm'

const STATUS_TAGS: Record<string, 'info' | 'success' | 'warning'> = {
  draft: 'info', published: 'success', disabled: 'warning',
}
const STATUS_LABELS: Record<string, string> = { draft: '草稿', published: '已发布', disabled: '已停用' }

const columns: HifyTableColumn<WorkflowSummary>[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: '描述', prop: 'description', minWidth: 180, hideOnNarrow: true },
  { label: '版本', prop: 'version', width: 80, align: 'right' },
  { label: '状态', slot: 'status', width: 95 },
  { label: '节点数', prop: 'nodeCount', width: 85, align: 'right' },
  { label: '更新时间', slot: 'updatedAt', width: 170, hideOnNarrow: true },
  { label: '操作', slot: 'actions', width: 180, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<WorkflowSummary>>()
const editRef = ref<{ open: (data?: Partial<WorkflowForm>) => void; formData: WorkflowForm }>()
const editVisible = ref(false)
const original = ref<WorkflowDetail | null>(null)
const deletingIds = ref(new Set<string>())
const filterStatus = ref<string>('')
const filterKeyword = ref('')
const definitionError = ref('')
let disposed = false

const runVisible = ref(false)
const runTarget = ref<WorkflowSummary | null>(null)
const runInput = ref('{\n  "question": "你好"\n}')
const runResult = ref<WorkflowRunResult | null>(null)
const running = ref(false)

const editTitle = computed(() => (original.value ? '编辑工作流' : '新建工作流'))
// 提示文案放脚本常量：模板里的 {{ }} 是插值，嵌套插值编译不过
const definitionHint = '节点与连线；变量用 {{变量名}} 引用'
const rules: FormRules<WorkflowForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  definition: [{ required: true, message: '工作流定义不能为空', trigger: 'blur' }],
}

function fetchWorkflows(params: { size: number; cursor?: string }) {
  return getWorkflowList({
    size: params.size,
    cursor: params.cursor,
    ...(filterStatus.value ? { status: filterStatus.value as WorkflowSummary['status'] } : {}),
    ...(filterKeyword.value.trim() ? { keyword: filterKeyword.value.trim() } : {}),
  })
}

const { execute: save, loading: saving } = useRequest(async (form: WorkflowForm) => {
  const existing = original.value
  return existing
    ? updateWorkflow(existing.publicId, workflowUpdatePayload(form, existing.version))
    : createWorkflow(workflowPayload(form))
})

async function openCreate(): Promise<void> {
  original.value = null
  definitionError.value = ''
  editRef.value?.open({ ...WORKFLOW_FORM_DEFAULTS, definition: WORKFLOW_SAMPLE })
}

async function openEdit(row: WorkflowSummary): Promise<void> {
  try {
    const detail = await getWorkflowDetail(row.publicId)
    if (disposed) return
    original.value = detail
    definitionError.value = ''
    editRef.value?.open({
      name: detail.name,
      description: detail.description ?? '',
      status: detail.status,
      definition: detail.definition,
    })
  } catch {
    // 失败由统一请求层提示
  }
}

async function handleSubmit(form: WorkflowForm): Promise<void> {
  const problem = validateDefinition(form.definition)
  if (problem) {
    definitionError.value = problem
    return
  }
  definitionError.value = ''
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新建工作流' : '已保存')
  editVisible.value = false
  await tableRef.value?.refresh({ reset: creating })
}

async function handleDelete(row: WorkflowSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除工作流“${row.name}”吗？`, () => deleteWorkflow(id), { successText: '已删除工作流' })
    if (await confirm()) await tableRef.value?.refresh()
  } finally { deletingIds.value.delete(id) }
}

function applyFormat(form: WorkflowForm): void {
  const formatted = formatDefinition(form.definition)
  if (!formatted) {
    definitionError.value = '不是合法的 JSON 对象'
    return
  }
  definitionError.value = ''
  form.definition = formatted
}

function applySample(form: WorkflowForm): void {
  form.definition = WORKFLOW_SAMPLE
  definitionError.value = ''
}

function openRun(row: WorkflowSummary): void {
  runTarget.value = row
  runResult.value = null
  runVisible.value = true
}

async function handleRun(): Promise<void> {
  const target = runTarget.value
  if (!target || running.value) return
  running.value = true
  try {
    const result = await runWorkflow(target.publicId, { input: runInput.value })
    if (disposed) return
    runResult.value = result
    if (result.status === 'succeeded') {
      ElMessage.success(`运行成功，执行了 ${result.stepCount} 个节点`)
    } else {
      ElMessage.error(result.errorMessage ?? '工作流执行失败')
    }
    await tableRef.value?.refresh()
  } catch {
    // 失败由统一请求层提示
  } finally { running.value = false }
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
    <PageHeader title="工作流" description="用 JSON 配置编排：模型调用、Agent、工具、知识检索、条件分支、输出六种节点。">
      <template #actions>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-button type="primary" :icon="Plus" :disabled="saving" @click="openCreate">新建工作流</el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="wf-filter">
        <el-select v-model="filterStatus" class="wf-filter__status" placeholder="全部状态" clearable
          @change="tableRef?.refresh({ reset: true })">
          <el-option v-for="option in WORKFLOW_STATUS_OPTIONS" :key="option.value" :label="option.label"
            :value="option.value" />
        </el-select>
        <el-input v-model="filterKeyword" class="wf-filter__keyword" placeholder="按名称或描述搜索" clearable
          @keyup.enter="tableRef?.refresh({ reset: true })" @clear="tableRef?.refresh({ reset: true })" />
        <el-button @click="tableRef?.refresh({ reset: true })">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchWorkflows" pagination-mode="cursor"
        row-key="publicId">
        <template #status="{ row }">
          <el-tag :type="STATUS_TAGS[row.status] ?? 'info'" effect="light">
            {{ STATUS_LABELS[row.status] ?? row.status }}
          </el-tag>
        </template>
        <template #updatedAt="{ row }"><span class="mono">{{ formatTime(row.updatedAt) }}</span></template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--run" :data-testid="`run-${row.publicId}`"
            @click="openRun(row)">运行</el-button>
          <el-button text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="editRef" v-model="editVisible" v-model:loading="saving" :title="editTitle"
      width="820px" label-width="90px" label-position="right" :rules="rules"
      :initial-values="WORKFLOW_FORM_DEFAULTS" @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个工作流起个名字" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="一句话说明它是干什么的" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width">
            <el-option v-for="option in WORKFLOW_STATUS_OPTIONS" :key="option.value" :label="option.label"
              :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="定义" prop="definition">
          <div class="wf-editor">
            <div class="wf-editor__bar">
              <el-button size="small" @click="applyFormat(form)">格式化</el-button>
              <el-button size="small" @click="applySample(form)">填入示例</el-button>
              <span class="wf-editor__hint">{{ definitionHint }}</span>
            </div>
            <el-input v-model="form.definition" type="textarea" :rows="18" class="mono" />
          </div>
          <div v-if="definitionError" class="wf-error">{{ definitionError }}</div>
        </el-form-item>
      </template>
    </HifyFormDialog>

    <el-dialog v-model="runVisible" title="运行工作流" width="720px" append-to-body>
      <div class="wf-run">
        <div class="wf-run__label">入参（JSON）</div>
        <el-input v-model="runInput" type="textarea" :rows="6" class="mono"
          placeholder='{ "question": "你好" }' />
        <el-button type="primary" :loading="running" data-testid="run-submit" @click="handleRun">
          运行
        </el-button>
        <div v-if="runResult" class="wf-run__result">
          <el-alert :type="runResult.status === 'succeeded' ? 'success' : 'error'" :closable="false"
            :title="runResult.status === 'succeeded' ? `运行成功（${runResult.stepCount} 个节点）` : '运行失败'"
            :description="runResult.errorMessage ?? ''" />
          <div class="wf-run__label">输出</div>
          <pre class="wf-run__output mono">{{ JSON.stringify(runResult.output, null, 2) }}</pre>
          <div class="wf-run__label">执行轨迹</div>
          <div v-for="step in runResult.steps" :key="step.nodeId" class="wf-run__step">
            <span class="wf-run__step-node mono">{{ step.nodeId }}</span>
            <span class="wf-run__step-type">{{ step.type }}</span>
            <span class="wf-run__step-output">{{ step.output }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit, .action-btn--run { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.wf-filter { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-4); }
.wf-filter__status { width: 160px; }
.wf-filter__keyword { width: 240px; }
.full-width { width: 100%; }
.wf-editor { width: 100%; }
.wf-editor__bar { display: flex; align-items: center; gap: var(--hify-space-2); margin-bottom: var(--hify-space-2); }
.wf-editor__hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.wf-error { color: var(--color-danger-500); font-size: var(--hify-font-size-sm); margin-top: var(--hify-space-1); }
.wf-run { display: flex; flex-direction: column; gap: var(--hify-space-3); }
.wf-run__label { font-size: var(--hify-font-size-sm); color: var(--color-text-secondary); }
.wf-run__result { margin-top: var(--hify-space-2); display: flex; flex-direction: column; gap: var(--hify-space-2); }
.wf-run__output { background: var(--color-bg-subtle); padding: var(--hify-space-3); border-radius: var(--hify-radius-sm);
  margin: 0; max-height: 240px; overflow: auto; font-size: var(--hify-font-size-sm); }
.wf-run__step { display: flex; gap: var(--hify-space-3); font-size: var(--hify-font-size-sm);
  padding: var(--hify-space-1) 0; }
.wf-run__step-node { color: var(--color-primary-600); }
.wf-run__step-type { color: var(--color-text-tertiary); min-width: 72px; }
.wf-run__step-output { color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; }
</style>
