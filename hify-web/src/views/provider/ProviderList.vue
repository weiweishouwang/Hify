<script setup lang="ts">
import { computed, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import type { FormRules } from 'element-plus'
import HifyTable, { type HifyTableColumn } from '@/components/HifyTable.vue'
import HifyFormDialog from '@/components/HifyFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useRequest } from '@/composables/useRequest'
import { notifySuccess } from '@/utils/notify'

/**
 * 接入点的协议档位。
 *
 * 存的是**协议**不是厂商名 —— DeepSeek、Qwen、vLLM 全是 `OpenAI` 这一档，
 * 它们之间只差一个 Base URL；厂商名放在「名称」里自由填写。
 */
type ProviderType = 'OpenAI' | 'Anthropic' | 'Ollama'

/** 一条模型接入点。 */
interface Provider {
  id: number
  /** 名称。 */
  name: string
  /** 类型。 */
  type: ProviderType
  /** 接口根地址。 */
  baseUrl: string
  /** 访问密钥。列表不展示，编辑时回填。 */
  apiKey: string
  /** 是否启用。 */
  enabled: boolean
  /** 创建时间。 */
  createdAt: string
}

/** 新增 / 编辑表单。 */
interface ProviderForm {
  name: string
  type: ProviderType
  apiKey: string
  baseUrl: string
}

const PROVIDER_TYPES: ProviderType[] = ['OpenAI', 'Anthropic', 'Ollama']

/**
 * Mock 数据。
 *
 * 页面先不接真实接口，用这 5 条撑住表格的样子 —— 类型铺开，四种都出现。
 */
const providers = ref<Provider[]>([
  {
    id: 1,
    name: 'OpenAI 官方',
    type: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: 'sk-mock-4f1c9a2b7e',
    enabled: true,
    createdAt: '2026-09-12 10:24',
  },
  {
    id: 2,
    name: 'Anthropic 官方',
    type: 'Anthropic',
    baseUrl: 'https://api.anthropic.com/v1',
    apiKey: 'sk-ant-mock-8d3e6f1a',
    enabled: true,
    createdAt: '2026-09-15 14:02',
  },
  {
    id: 3,
    name: 'Google AI Studio（OpenAI 兼容）',
    type: 'OpenAI',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    apiKey: 'AIza-mock-0b5e7c93',
    enabled: false,
    createdAt: '2026-09-20 09:41',
  },
  {
    id: 4,
    name: '本地 Ollama',
    type: 'Ollama',
    baseUrl: 'http://127.0.0.1:11434',
    apiKey: 'ollama-local',
    enabled: true,
    createdAt: '2026-09-28 16:37',
  },
  {
    id: 5,
    name: 'DeepSeek',
    type: 'OpenAI',
    baseUrl: 'https://api.deepseek.com/v1',
    apiKey: 'sk-mock-2a7c60f4',
    enabled: false,
    createdAt: '2026-10-08 11:15',
  },
])

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 假接口：按分页切一刀返回。延时是为了让表格的 loading 态看得见。 */
async function fetchProviders(params: { page: number; size: number }): Promise<{
  data: Provider[]
  total: number
}> {
  await delay(200)
  const start = (params.page - 1) * params.size
  return {
    data: providers.value.slice(start, start + params.size),
    total: providers.value.length,
  }
}

const columns: HifyTableColumn<Provider>[] = [
  { label: '名称', prop: 'name', minWidth: 160 },
  { label: '类型', prop: 'type', width: 110 },
  { label: 'Base URL', slot: 'baseUrl', minWidth: 260, hideOnNarrow: true },
  { label: '状态', slot: 'status', width: 100 },
  { label: '创建时间', prop: 'createdAt', width: 160, hideOnNarrow: true },
  { label: '操作', slot: 'actions', width: 160, fixed: 'right' },
]

const tableRef = ref<{ refresh: () => Promise<void> }>()

const rules: FormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  // API Key 不设必填：Ollama / vLLM / LM Studio 这类本地服务不鉴权。
  // 填错了会在「连通性测试」里以鉴权失败暴露，比在这里按格式猜强。
  baseUrl: [
    { required: true, message: 'Base URL 不能为空', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: 'Base URL 要以 http:// 或 https:// 开头',
      trigger: 'blur',
    },
  ],
}

const dialogVisible = ref(false)
const dialogRef = ref<{ open: (data?: Partial<ProviderForm>) => void }>()

/**
 * Base URL 的示例。
 *
 * **放在脚本里再绑定**：模板属性值里出现 `//`（比如一个网址）会让 vue-tsc
 * 把后面当成注释，类型检查报「找不到 form」这种假错。绑定写法没这个问题。
 */
const baseUrlHint = 'https://api.openai.com/v1'

/**
 * 表单默认值。
 *
 * **显式标注类型**：`initial-values` 的类型就是 HifyFormDialog 推断泛型参数的依据。
 * 写成裸字面量的话 `type` 会被推成 string，泛型就散了；`satisfies` 保住窄类型。
 */
const formDefaults = {
  name: '',
  type: 'OpenAI' as ProviderType,
  apiKey: '',
  baseUrl: '',
} satisfies Partial<ProviderForm>

/** 编辑中的 id；为 null 表示新增。 */
const editingId = ref<number | null>(null)

const dialogTitle = computed(() => (editingId.value === null ? '新增模型提供商' : '编辑模型提供商'))

/** 保存：新增就插一条，编辑就改那一条。 */
const { execute: save, loading: saving } = useRequest(
  async (payload: { id: number | null; form: ProviderForm }) => {
    await delay(300)
    if (payload.id === null) {
      providers.value = [
        {
          id: Math.max(0, ...providers.value.map((p) => p.id)) + 1,
          ...payload.form,
          enabled: true,
          createdAt: formatNow(),
        },
        ...providers.value,
      ]
    } else {
      providers.value = providers.value.map((p) =>
        p.id === payload.id ? { ...p, ...payload.form } : p,
      )
    }
    return payload
  },
)

function formatNow(): string {
  const now = new Date()
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}`
}

function openCreate(): void {
  editingId.value = null
  showApiKey.value = false
  dialogRef.value?.open({ ...formDefaults })
}

function openEdit(row: Provider): void {
  editingId.value = row.id
  showApiKey.value = false
  dialogRef.value?.open({
    name: row.name,
    type: row.type,
    apiKey: row.apiKey,
    baseUrl: row.baseUrl,
  })
}

async function handleSubmit(form: ProviderForm): Promise<void> {
  const id = editingId.value
  const saved = await save({ id, form })
  if (!saved) {
    return
  }
  notifySuccess(id === null ? '已新增模型提供商' : '已保存')
  dialogVisible.value = false
  await tableRef.value?.refresh()
}

/** 待删除的 id：确认框里要用。 */
const pendingDeleteId = ref<number | null>(null)

/** API Key 是否明文显示。默认藏起来。 */
const showApiKey = ref(false)

const confirmDelete = useConfirm(
  '确定删除这个模型提供商吗？删除后用它的 Agent 会调用失败。',
  async () => {
    await delay(200)
    providers.value = providers.value.filter((p) => p.id !== pendingDeleteId.value)
  },
)

async function handleDelete(row: Provider): Promise<void> {
  pendingDeleteId.value = row.id
  if (await confirmDelete()) {
    await tableRef.value?.refresh()
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="模型提供商管理"
      description="统一管理模型接入点：OpenAI 兼容端点、Anthropic Messages 与本地 Ollama 的连接配置。"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          新增提供商
        </el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <HifyTable ref="tableRef" :columns="columns" :api="fetchProviders">
        <!-- Base URL 是机器可读的值，用等宽字体 -->
        <template #baseUrl="{ row }">
          <span class="mono">{{ row.baseUrl }}</span>
        </template>

        <template #status="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" effect="light">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>

        <template #actions="{ row }">
          <el-button text class="action-btn action-btn--edit" @click="openEdit(row)">编辑</el-button>
          <el-button text class="action-btn action-btn--delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </HifyTable>
    </div>

    <HifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      v-model:loading="saving"
      :title="dialogTitle"
      width="520px"
      label-width="100px"
      label-position="right"
      :rules="rules"
      :initial-values="formDefaults"
      @submit="handleSubmit"
    >
      <template #default="{ form }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给这个接入点起个名字" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="选一个协议" style="width: 100%">
            <el-option v-for="t in PROVIDER_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            :type="showApiKey ? 'text' : 'password'"
            placeholder="上游服务的访问密钥，本地服务可留空"
          >
            <template #suffix>
              <button type="button" class="field-toggle" @click="showApiKey = !showApiKey">
                {{ showApiKey ? '隐藏' : '显示' }}
              </button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" :placeholder="baseUrlHint" />
        </el-form-item>
      </template>
    </HifyFormDialog>
  </div>
</template>

<style scoped>
/*
 * 操作列的两个按钮。
 *
 * text 类型按钮默认左右各带 16px 内边距，两个挨在一起内容会比列宽还宽、
 * 被 show-overflow-tooltip 截成省略号 —— 这里把内边距去掉，间距全交给
 * margin 控制（Element 相邻按钮默认 12px，再加 8px = 20px）。
 */
.action-btn {
  padding-left: 0;
  padding-right: 0;
}

.action-btn + .action-btn {
  margin-left: 20px;
}

/* text 类型没有自己的颜色，这里显式给：编辑蓝、删除红 */
.action-btn.action-btn--edit {
  color: var(--color-primary-600);
}

.action-btn.action-btn--edit:hover {
  color: var(--color-primary-700);
}

.action-btn.action-btn--delete {
  color: var(--color-danger-500);
}

.action-btn.action-btn--delete:hover {
  color: var(--color-danger-600);
}

/* API Key 的显示 / 隐藏切换 */
.field-toggle {
  padding: 0 4px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: var(--hify-font-size-xs);
  line-height: 1;
  cursor: pointer;
  transition: var(--hify-transition-color);
}

.field-toggle:hover {
  color: var(--color-primary-600);
}
</style>
