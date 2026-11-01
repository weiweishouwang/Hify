<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createKnowledgeBase, deleteKnowledgeBase, getEmbeddingModels, getKnowledgeBaseList, updateKnowledgeBase,
} from '@/api/knowledge'
import type { EmbeddingModelOption, KnowledgeBaseSummary } from '@/types/knowledge'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'

interface KnowledgeBaseForm extends Record<string, unknown> {
  name: string
  description: string
  embeddingModel: string
  chunkSize: number
  chunkOverlap: number
  status: 'active' | 'disabled'
}

const router = useRouter()
const columns: HifyTableColumn<KnowledgeBaseSummary>[] = [
  { label: '名称', prop: 'name', minWidth: 160 },
  { label: '描述', prop: 'description', minWidth: 200, hideOnNarrow: true, formatter: row => row.description || '—' },
  { label: '向量模型', slot: 'model', minWidth: 170 },
  { label: '分块', slot: 'chunk', width: 130, hideOnNarrow: true },
  { label: '文档数', prop: 'docCount', width: 90, align: 'right' },
  { label: '状态', slot: 'status', width: 85 },
  { label: '操作', slot: 'actions', width: 175, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<KnowledgeBaseSummary>>()
const dialogRef = ref<{ open: (data?: Partial<KnowledgeBaseForm>) => void; formData: KnowledgeBaseForm }>()
const dialogVisible = ref(false)
const original = ref<KnowledgeBaseSummary | null>(null)
const deletingIds = ref(new Set<string>())
const filterStatus = ref<'active' | 'disabled' | ''>('')
const filterKeyword = ref('')
const modelOptions = ref<EmbeddingModelOption[]>([])
const modelLoading = ref(false)
let disposed = false

const dialogTitle = computed(() => (original.value ? '编辑知识库' : '新建知识库'))
const formDefaults = {
  name: '', description: '', embeddingModel: '', chunkSize: 500, chunkOverlap: 50, status: 'active',
} satisfies Partial<KnowledgeBaseForm>
const rules: FormRules<KnowledgeBaseForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请选择向量化模型', trigger: 'change' }],
  chunkSize: [{ required: true, type: 'number', min: 100, max: 4000, message: '分块大小要在 100 到 4000 之间', trigger: 'blur' }],
  chunkOverlap: [{ required: true, type: 'number', min: 0, message: '重叠不能为负数', trigger: 'blur' }],
}

function fetchKnowledgeBases(params: { size: number; cursor?: string }) {
  return getKnowledgeBaseList({
    size: params.size,
    cursor: params.cursor,
    ...(filterStatus.value ? { status: filterStatus.value } : {}),
    ...(filterKeyword.value.trim() ? { keyword: filterKeyword.value.trim() } : {}),
  })
}

const { execute: save, loading: saving } = useRequest(async (form: KnowledgeBaseForm) => {
  const existing = original.value
  const payload = {
    name: form.name.trim(),
    description: form.description.trim(),
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    status: form.status,
  }
  return existing
    ? updateKnowledgeBase(existing.publicId, { ...payload, configVersion: existing.configVersion })
    : createKnowledgeBase({ ...payload, embeddingModel: form.embeddingModel })
})

async function loadModelOptions(): Promise<void> {
  if (modelOptions.value.length || modelLoading.value) return
  modelLoading.value = true
  try {
    modelOptions.value = await getEmbeddingModels()
    if (!modelOptions.value.length) {
      ElMessage.warning('还没有可用的 embedding 模型：先在模型管理里同步上游模型，并把其中一个标成 embedding')
    }
  } catch {
    // 失败由请求层提示
  } finally { modelLoading.value = false }
}

async function openCreate(): Promise<void> {
  original.value = null
  await loadModelOptions()
  dialogRef.value?.open()
}

async function openEdit(row: KnowledgeBaseSummary): Promise<void> {
  original.value = row
  await loadModelOptions()
  dialogRef.value?.open({
    name: row.name,
    description: row.description ?? '',
    embeddingModel: row.embeddingModel ?? '',
    chunkSize: row.chunkSize ?? 500,
    chunkOverlap: row.chunkOverlap ?? 50,
    status: row.status,
  })
}

async function handleSubmit(form: KnowledgeBaseForm): Promise<void> {
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新建知识库' : '已保存')
  dialogVisible.value = false
  await tableRef.value?.refresh({ reset: creating })
}

async function handleDelete(row: KnowledgeBaseSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？库里有文档时需要先删文档。`, () => deleteKnowledgeBase(id), { successText: '已删除知识库' })
    if (await confirm()) await tableRef.value?.refresh()
  } finally { deletingIds.value.delete(id) }
}

function openDocuments(row: KnowledgeBaseSummary): void {
  if (!row.publicId) return
  void router.push({ name: 'knowledge-detail', params: { id: row.publicId } })
}

function modelLabel(publicId?: string): string {
  if (!publicId) return '—'
  const option = modelOptions.value.find(item => item.publicId === publicId)
  return option?.displayName ?? option?.modelName ?? publicId
}

onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <div class="page">
    <PageHeader title="知识库" description="上传文档、固定分块、向量化，供 Agent 检索增强。一期只支持 TXT。">
      <template #actions>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-button type="primary" :disabled="saving" @click="openCreate">
          <el-icon><Plus /></el-icon>新建知识库
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="kb-filter">
        <el-select v-model="filterStatus" class="kb-filter__status" placeholder="全部状态" clearable
          @change="tableRef?.refresh({ reset: true })">
          <el-option label="可用" value="active" />
          <el-option label="停用" value="disabled" />
        </el-select>
        <el-input v-model="filterKeyword" class="kb-filter__keyword" placeholder="按名称或描述搜索" clearable
          @keyup.enter="tableRef?.refresh({ reset: true })" @clear="tableRef?.refresh({ reset: true })" />
        <el-button :icon="Search" @click="tableRef?.refresh({ reset: true })">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchKnowledgeBases" pagination-mode="cursor" row-key="publicId">
        <template #model="{ row }"><span class="mono">{{ modelLabel(row.embeddingModel) }}</span></template>
        <template #chunk="{ row }">
          <span class="mono">{{ row.chunkSize ?? '—' }} / {{ row.chunkOverlap ?? '—' }}</span>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" effect="light">
            {{ row.status === 'active' ? '可用' : '停用' }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--edit" :data-testid="`open-${row.publicId}`"
            @click="openDocuments(row)">文档</el-button>
          <el-button text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" v-model:loading="saving" :title="dialogTitle"
      width="560px" label-width="110px" label-position="right" :rules="rules" :initial-values="formDefaults" @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个知识库起个名字" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="这个知识库放什么内容" />
        </el-form-item>
        <el-form-item label="向量模型" prop="embeddingModel">
          <el-select v-model="form.embeddingModel" class="full-width" :loading="modelLoading" :disabled="!!original"
            placeholder="选择用于向量化的模型">
            <el-option v-for="option in modelOptions" :key="option.publicId"
              :label="`${option.displayName ?? option.modelName}（${option.providerName ?? ''} · ${option.embeddingDimension ?? '?'} 维）`"
              :value="option.publicId" />
          </el-select>
          <span class="field-hint">向量维度必须与向量库的 1536 一致；没有可选模型时先在模型管理里同步并标记。</span>
        </el-form-item>
        <el-form-item label="分块大小" prop="chunkSize">
          <el-input-number v-model="form.chunkSize" :min="100" :max="4000" :step="50" />
          <span class="field-hint">单位字符。一期只做固定分块，不按段落切。</span>
        </el-form-item>
        <el-form-item label="分块重叠" prop="chunkOverlap">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="2000" :step="10" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="full-width">
            <el-option label="可用" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
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
.kb-filter { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-4); }
.kb-filter__status { width: 130px; }
.kb-filter__keyword { width: 260px; }
.full-width { width: 100%; }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
</style>
