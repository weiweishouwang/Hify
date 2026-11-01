<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn, type HifyTableHandle } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  createMcpServer, deleteMcpServer, getMcpServerList, syncMcpTools, testMcpServer, updateMcpServer,
} from '@/api/mcp'
import type { McpServerStatus, McpServerSummary, McpTransport } from '@/types/mcp'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'
import { formFromDetail, mcpPayload, mcpUpdatePayload, type McpForm } from './mcpForm'

const TRANSPORTS: Array<{ value: McpTransport; label: string }> = [
  { value: 'http', label: 'HTTP（Streamable HTTP）' },
  { value: 'stdio', label: 'stdio（本地进程）' },
]
const STATUS_TAGS: Record<McpServerStatus, 'info' | 'success' | 'danger'> = {
  unknown: 'info', online: 'success', offline: 'danger',
}
const STATUS_LABELS: Record<McpServerStatus, string> = {
  unknown: '未探测', online: '在线', offline: '离线',
}

const columns: HifyTableColumn<McpServerSummary>[] = [
  { label: '名称', prop: 'name', minWidth: 150 },
  { label: '传输方式', slot: 'transport', width: 110 },
  { label: '地址 / 命令', slot: 'target', minWidth: 220 },
  { label: '状态', slot: 'status', width: 95 },
  { label: '工具数', prop: 'toolCount', width: 85, align: 'right' },
  { label: '最近探测', slot: 'checkedAt', width: 170, hideOnNarrow: true },
  { label: '操作', slot: 'actions', width: 200, fixed: 'right' },
]

const tableRef = ref<HifyTableHandle<McpServerSummary>>()
const dialogRef = ref<{ open: (data?: Partial<McpForm>) => void; formData: McpForm }>()
const dialogVisible = ref(false)
const original = ref<McpServerSummary | null>(null)
const deletingIds = ref(new Set<string>())
const testingIds = ref(new Set<string>())
const syncing = ref(false)
const filterTransport = ref<McpTransport | ''>('')
const filterKeyword = ref('')
let disposed = false

const dialogTitle = computed(() => (original.value ? '编辑 MCP 服务' : '新增 MCP 服务'))
// 地址示例放脚本常量：模板属性值里出现 // 会被 vue-tsc 当成注释，元素没闭合，
// 后面的兄弟节点全丢掉插槽作用域（CLAUDE.md §3.6）。
const urlHint = '例如 http://127.0.0.1:9000/mcp'
const commandHint = '例如 npx -y @modelcontextprotocol/server-filesystem'
const formDefaults: McpForm = {
  name: '', transport: 'http', command: '', url: '',
  argsText: '', envText: '', headersText: '', enabled: true,
}
const rules: FormRules<McpForm> = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  transport: [{ required: true, message: '请选择传输方式', trigger: 'change' }],
  command: [{ validator: (_rule, value: unknown, callback) => {
    const current = dialogRef.value?.formData
    if (current?.transport === 'stdio' && !(typeof value === 'string' && value.trim())) {
      callback(new Error('stdio 传输必须填写启动命令'))
    } else callback()
  }, trigger: 'blur' }],
  url: [{ validator: (_rule, value: unknown, callback) => {
    const current = dialogRef.value?.formData
    if (current?.transport === 'http' && !(typeof value === 'string' && value.trim())) {
      callback(new Error('http 传输必须填写服务地址'))
    } else callback()
  }, trigger: 'blur' }],
}

function fetchMcpServers(params: { size: number; cursor?: string }) {
  return getMcpServerList({
    size: params.size,
    cursor: params.cursor,
    ...(filterTransport.value ? { transport: filterTransport.value } : {}),
    ...(filterKeyword.value.trim() ? { keyword: filterKeyword.value.trim() } : {}),
  })
}

const { execute: save, loading: saving } = useRequest(async (form: McpForm) => {
  const existing = original.value
  return existing
    ? updateMcpServer(existing.publicId, mcpUpdatePayload(form, existing))
    : createMcpServer(mcpPayload(form))
})

function openCreate(): void {
  original.value = null
  dialogRef.value?.open()
}

function openEdit(row: McpServerSummary): void {
  original.value = row
  dialogRef.value?.open(formFromDetail(row))
}

async function handleSubmit(form: McpForm): Promise<void> {
  const creating = original.value === null
  const result = await save(form)
  if (!result || disposed) return
  notifySuccess(creating ? '已新增 MCP 服务' : '已保存')
  dialogVisible.value = false
  await tableRef.value?.refresh({ reset: creating })
}

async function handleDelete(row: McpServerSummary): Promise<void> {
  if (!row.publicId || deletingIds.value.has(row.publicId)) return
  const id = row.publicId
  deletingIds.value.add(id)
  try {
    const confirm = useConfirm(`确定删除“${row.name}”吗？`, () => deleteMcpServer(id), { successText: '已删除 MCP 服务' })
    if (await confirm()) await tableRef.value?.refresh()
  } finally { deletingIds.value.delete(id) }
}

async function handleTest(row: McpServerSummary): Promise<void> {
  if (!row.publicId || testingIds.value.has(row.publicId)) return
  testingIds.value.add(row.publicId)
  try {
    const result = await testMcpServer(row.publicId)
    if (disposed) return
    if (result.connected) {
      ElMessage.success(`连接成功：${result.toolCount} 个工具${result.protocolVersion ? '（协议 ' + result.protocolVersion + '）' : ''}`)
      await tableRef.value?.refresh()
    } else {
      ElMessage.warning(result.message ?? '连接失败')
      await tableRef.value?.refresh()
    }
  } catch {
    // 失败由统一请求层提示
  } finally { testingIds.value.delete(row.publicId) }
}

async function handleSync(): Promise<void> {
  if (syncing.value) return
  syncing.value = true
  try {
    const result = await syncMcpTools()
    if (disposed) return
    ElMessage.success(`已同步：发现 ${result.discovered} 个工具，${result.failed} 个服务连不上`)
    await tableRef.value?.refresh()
  } catch {
    // 失败由统一请求层提示
  } finally { syncing.value = false }
}

function targetOf(row: McpServerSummary): string {
  return row.transport === 'stdio' ? (row.command ?? '—') : (row.url ?? '—')
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
    <PageHeader title="MCP 服务" description="接入 MCP server，把它的工具接进统一工具目录，供 Agent 绑定。">
      <template #actions>
        <el-button :icon="Refresh" @click="tableRef?.refresh()">刷新</el-button>
        <el-button :loading="syncing" @click="handleSync">同步工具</el-button>
        <el-button type="primary" :disabled="saving" @click="openCreate">
          <el-icon><Plus /></el-icon>新增服务
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <div class="mcp-filter">
        <el-select v-model="filterTransport" class="mcp-filter__transport" placeholder="全部传输方式" clearable
          @change="tableRef?.refresh({ reset: true })">
          <el-option v-for="option in TRANSPORTS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-input v-model="filterKeyword" class="mcp-filter__keyword" placeholder="按名称搜索" clearable
          @keyup.enter="tableRef?.refresh({ reset: true })" @clear="tableRef?.refresh({ reset: true })" />
        <el-button :icon="Search" @click="tableRef?.refresh({ reset: true })">搜索</el-button>
      </div>

      <HifyTable ref="tableRef" :columns="columns" :api="fetchMcpServers" pagination-mode="cursor" row-key="publicId">
        <template #transport="{ row }">
          <el-tag :type="row.transport === 'http' ? 'primary' : 'warning'" effect="light">
            {{ row.transport === 'http' ? 'HTTP' : 'stdio' }}
          </el-tag>
        </template>
        <template #target="{ row }"><span class="mono">{{ targetOf(row) }}</span></template>
        <template #status="{ row }">
          <el-tag :type="STATUS_TAGS[row.status] ?? 'info'" effect="light">
            {{ STATUS_LABELS[row.status] ?? row.status }}
          </el-tag>
        </template>
        <template #checkedAt="{ row }"><span class="mono">{{ formatTime(row.lastCheckedAt) }}</span></template>
        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--test" :data-testid="`test-${row.publicId}`"
            :loading="testingIds.has(row.publicId)" @click="handleTest(row)">测试连接</el-button>
          <el-button text class="action-btn action-btn--edit" :data-testid="`edit-${row.publicId}`"
            :disabled="saving" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--delete" :data-testid="`delete-${row.publicId}`"
            :loading="deletingIds.has(row.publicId)" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog ref="dialogRef" v-model="dialogVisible" v-model:loading="saving" :title="dialogTitle"
      width="600px" label-width="110px" label-position="right" :rules="rules" :initial-values="formDefaults"
      @submit="handleSubmit">
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个 MCP 服务起个名字" />
        </el-form-item>
        <el-form-item label="传输方式" prop="transport">
          <el-select v-model="form.transport" class="full-width">
            <el-option v-for="option in TRANSPORTS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.transport === 'stdio'" label="启动命令" prop="command">
          <el-input v-model="form.command" :placeholder="commandHint" />
        </el-form-item>
        <el-form-item v-if="form.transport === 'stdio'" label="命令参数" prop="argsText">
          <el-input v-model="form.argsText" type="textarea" :rows="3" class="mono" placeholder="一行一个参数" />
        </el-form-item>
        <el-form-item v-if="form.transport === 'stdio'" label="环境变量" prop="envText">
          <el-input v-model="form.envText" type="textarea" :rows="3" class="mono" placeholder="一行一个 key=value" />
        </el-form-item>
        <el-form-item v-if="form.transport === 'http'" label="服务地址" prop="url">
          <el-input v-model="form.url" :placeholder="urlHint" />
        </el-form-item>
        <el-form-item v-if="form.transport === 'http'" label="请求头" prop="headersText">
          <el-input v-model="form.headersText" type="textarea" :rows="3" class="mono" placeholder="一行一个 key=value" />
          <span class="field-hint">含 token 的请求头会加密存储；编辑时显示为已配置，清空即删除。</span>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
.action-btn { padding-left: 0; padding-right: 0; }
.action-btn + .action-btn { margin-left: var(--hify-space-4); }
.action-btn--edit, .action-btn--test { color: var(--color-primary-600); }
.action-btn--delete { color: var(--color-danger-500); }
.mcp-filter { display: flex; align-items: center; gap: var(--hify-space-3); margin-bottom: var(--hify-space-4); }
.mcp-filter__transport { width: 200px; }
.mcp-filter__keyword { width: 240px; }
.full-width { width: 100%; }
.field-hint { color: var(--color-text-tertiary); font-size: var(--hify-font-size-xs); }
</style>
