<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type UploadRequestOptions } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  deleteDocument, getDocumentList, getKnowledgeBaseDetail, reindexDocument, searchKnowledge, uploadDocument,
} from '@/api/knowledge'
import type { DocumentStatus, DocumentSummary, KnowledgeBaseSummary, SearchResult } from '@/types/knowledge'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess } from '@/utils/notify'

const STATUS_TAGS: Record<DocumentStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  uploaded: 'info', indexing: 'warning', ready: 'success', failed: 'danger', deleting: 'info',
}
const STATUS_LABELS: Record<DocumentStatus, string> = {
  uploaded: '待索引', indexing: '索引中', ready: '就绪', failed: '失败', deleting: '删除中',
}

const route = useRoute()
const router = useRouter()
const kbPublicId = computed(() => String(route.params.id ?? ''))

const columns: HifyTableColumn<DocumentSummary>[] = [
  { label: '文档', prop: 'name', minWidth: 200 },
  { label: '类型', prop: 'fileType', width: 80 },
  { label: '大小', slot: 'size', width: 100, hideOnNarrow: true },
  { label: '状态', slot: 'status', width: 120 },
  { label: '分段数', prop: 'chunkCount', width: 90, align: 'right' },
  { label: '索引时间', slot: 'indexedAt', width: 170, hideOnNarrow: true },
  { label: '操作', slot: 'actions', width: 165, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<DocumentSummary>>()
const knowledgeBase = ref<KnowledgeBaseSummary | null>(null)
const uploading = ref(false)
const deletingIds = ref(new Set<string>())
const reindexingIds = ref(new Set<string>())
const searchQuery = ref('')
const searchTopK = ref(5)
const searching = ref(false)
const searchResults = ref<SearchResult[] | null>(null)
let disposed = false

function fetchDocuments(params: { size: number; cursor?: string }) {
  return getDocumentList(kbPublicId.value, { size: params.size, cursor: params.cursor })
}

async function loadKnowledgeBase(): Promise<void> {
  try {
    knowledgeBase.value = await getKnowledgeBaseDetail(kbPublicId.value)
  } catch {
    // 失败由请求层提示
  }
}

async function handleUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  try {
    await uploadDocument(kbPublicId.value, options.file)
    notifySuccess('已上传，索引在后台进行')
    await tableRef.value?.refresh({ reset: true })
    await loadKnowledgeBase()
  } catch {
    // 失败由请求层提示
  } finally {
    uploading.value = false
    options.onSuccess?.({})
  }
}

async function handleDelete(row: DocumentSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？`, () => deleteDocument(id), { successText: '已删除文档' })
    if (await confirm()) {
      await tableRef.value?.refresh()
      await loadKnowledgeBase()
    }
  } finally { deletingIds.value.delete(id) }
}

async function handleReindex(row: DocumentSummary): Promise<void> {
  if (!row.publicId || reindexingIds.value.has(row.publicId)) return
  const id = row.publicId
  reindexingIds.value.add(id)
  try {
    await reindexDocument(id)
    ElMessage.success('已重新提交索引')
    await tableRef.value?.refresh()
  } catch {
    // 失败由请求层提示
  } finally { reindexingIds.value.delete(id) }
}

async function runSearch(): Promise<void> {
  if (!searchQuery.value.trim()) {
    ElMessage.error('请输入检索内容')
    return
  }
  searching.value = true
  try {
    const results = await searchKnowledge(kbPublicId.value, { query: searchQuery.value.trim(), topK: searchTopK.value })
    if (!disposed) searchResults.value = results
  } catch {
    // 失败由请求层提示
  } finally { searching.value = false }
}

function goBack(): void {
  void router.push({ name: 'knowledge' })
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const time = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('zh-CN', { hour12: false })
}

function formatSize(size?: number): string {
  if (size == null) return '—'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <div class="page">
    <PageHeader :title="knowledgeBase?.name ?? '知识库文档'"
      :description="knowledgeBase?.description || '管理这个知识库下的文档，并试试检索效果。'">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-upload :show-file-list="false" :http-request="handleUpload" accept=".txt">
          <el-button type="primary" :loading="uploading">上传 TXT</el-button>
        </el-upload>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="kb-summary">
        <span>向量模型：<span class="mono">{{ knowledgeBase?.embeddingModel ?? '—' }}</span></span>
        <span>分块：<span class="mono">{{ knowledgeBase?.chunkSize ?? '—' }} / {{ knowledgeBase?.chunkOverlap ?? '—' }}</span></span>
        <span>文档数：{{ knowledgeBase?.docCount ?? 0 }}</span>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchDocuments" pagination-mode="cursor" row-key="publicId">
        <template #size="{ row }"><span class="mono">{{ formatSize(row.fileSize) }}</span></template>
        <template #status="{ row }">
          <el-tag :type="STATUS_TAGS[row.status] ?? 'info'" effect="light"
            :title="row.errorMessage ?? ''">{{ STATUS_LABELS[row.status] ?? row.status }}</el-tag>
        </template>
        <template #indexedAt="{ row }"><span class="mono">{{ formatTime(row.indexedAt) }}</span></template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--edit" :data-testid="`reindex-${row.publicId}`"
            :loading="reindexingIds.has(row.publicId)" @click="handleReindex(row)">重新索引</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <div class="page-card">
      <div class="search-head">
        <h3 class="search-title">检索测试</h3>
        <span class="field-hint">用知识库配置的向量模型向量化查询，再在 PG 里做余弦检索。</span>
      </div>
      <div class="search-bar">
        <el-input v-model="searchQuery" type="textarea" :rows="2" class="search-input"
          placeholder="输入要检索的问题，看看能召回哪些分段" />
        <el-input-number v-model="searchTopK" :min="1" :max="20" class="search-topk" />
        <el-button type="primary" :icon="Search" :loading="searching" @click="runSearch">检索</el-button>
      </div>
      <div v-if="searchResults" class="search-results">
        <p v-if="!searchResults.length" class="field-hint">没有命中任何分段 —— 文档可能还没索引完，或查询与内容无关。</p>
        <article v-for="result in searchResults" :key="result.chunkId" class="search-hit">
          <div class="search-hit__head">
            <span class="search-hit__doc">{{ result.docName ?? '未知文档' }}</span>
            <span class="search-hit__index mono">#{{ result.chunkIndex }}</span>
            <span class="search-hit__score mono">{{ result.score.toFixed(4) }}</span>
          </div>
          <p class="search-hit__content">{{ result.content }}</p>
        </article>
      </div>
    </div>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.kb-summary { display: flex; gap: var(--hify-space-6); margin-bottom: var(--hify-space-4); color: var(--color-text-secondary); font-size: var(--hify-font-size-sm); }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.search-head { display: flex; align-items: baseline; gap: var(--hify-space-3); margin-bottom: var(--hify-space-3); }
.search-title { margin: 0; font-size: var(--hify-font-size-md); font-weight: 600; }
.search-bar { display: flex; align-items: flex-start; gap: var(--hify-space-3); }
.search-input { flex: 1; }
.search-topk { width: 120px; }
.search-results { margin-top: var(--hify-space-4); display: flex; flex-direction: column; gap: var(--hify-space-3); }
.search-hit { border: 1px solid var(--color-border); border-radius: var(--hify-radius-sm); padding: var(--hify-space-3); }
.search-hit__head { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-2); }
.search-hit__doc { font-weight: 600; }
.search-hit__index, .search-hit__score { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
.search-hit__content { margin: 0; white-space: pre-wrap; color: var(--color-text-secondary); }
</style>
