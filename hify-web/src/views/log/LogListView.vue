<script setup lang="ts">
import { ref } from 'vue'
import HifyTable, { type HifyTableColumn } from '@/components/HifyTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import { getConversationRuns, getWorkflowRuns } from '@/api/log'
import type { ConversationRun, RunStatus, WorkflowRunLog } from '@/types/log'

/**
 * 控制台的「日志」页。
 *
 * 两类记录并排看：对话的每一轮运行、工作流的每一次运行。
 * 它们分属两个模块，各有各的列表接口 —— 这里不做跨模块聚合，
 * 翻页、筛选也各自独立。
 */
const activeTab = ref<'conversation' | 'workflow'>('conversation')

const STATUS_TAGS: Record<RunStatus, 'success' | 'danger' | 'warning'> = {
  succeeded: 'success',
  failed: 'danger',
  running: 'warning',
}
const STATUS_LABELS: Record<RunStatus, string> = {
  succeeded: '成功',
  failed: '失败',
  running: '进行中',
}

const conversationColumns: HifyTableColumn<ConversationRun>[] = [
  { label: '时间', prop: 'createdAt', width: 170, hideOnNarrow: true, formatter: row => formatTime(row.createdAt) },
  { label: '会话', prop: 'sessionTitle', minWidth: 200, formatter: row => row.sessionTitle || '（未命名会话）' },
  { label: 'Agent', prop: 'agentName', width: 140, hideOnNarrow: true, formatter: row => row.agentName || '—' },
  { label: '状态', slot: 'conversationStatus', width: 90 },
  { label: '输入 token', prop: 'promptTokens', width: 100, align: 'right', hideOnNarrow: true },
  { label: '输出 token', prop: 'completionTokens', width: 100, align: 'right', hideOnNarrow: true },
  { label: '工具调用', prop: 'toolCallCount', width: 90, align: 'right', hideOnNarrow: true },
  { label: '耗时', prop: 'latencyMs', width: 90, align: 'right', formatter: row => formatLatency(row.latencyMs) },
  { label: '错误', slot: 'conversationError', minWidth: 200 },
]

const workflowColumns: HifyTableColumn<WorkflowRunLog>[] = [
  { label: '时间', prop: 'createdAt', width: 170, hideOnNarrow: true, formatter: row => formatTime(row.createdAt) },
  { label: '工作流', prop: 'definitionName', minWidth: 180, formatter: row => row.definitionName || '（已删除的工作流）' },
  { label: '状态', slot: 'workflowStatus', width: 90 },
  { label: '节点数', prop: 'stepCount', width: 90, align: 'right' },
  { label: '耗时', prop: 'latencyMs', width: 90, align: 'right', formatter: row => formatLatency(row.latencyMs) },
  { label: '入参', prop: 'inputPreview', minWidth: 200, hideOnNarrow: true, formatter: row => row.inputPreview || '—' },
  { label: '出参', prop: 'outputPreview', minWidth: 200, hideOnNarrow: true, formatter: row => row.outputPreview || '—' },
  { label: '错误', slot: 'workflowError', minWidth: 200 },
]

function formatTime(value?: string): string {
  if (!value) return '—'
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('zh-CN', { hour12: false })
}

function formatLatency(value?: number): string {
  return value === undefined || value === null ? '—' : `${value} ms`
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="日志"
      description="查看每轮对话与每次工作流的运行记录：状态、token、耗时与失败原因。"
    />

    <div class="page-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="对话记录" name="conversation">
          <HifyTable :columns="conversationColumns" :api="getConversationRuns" pagination-mode="cursor" row-key="id"
            empty-text="还没有对话记录">
            <template #conversationStatus="{ row }">
              <el-tag :type="STATUS_TAGS[row.status as RunStatus] ?? 'info'" effect="light" size="small">
                {{ STATUS_LABELS[row.status as RunStatus] ?? row.status }}
              </el-tag>
            </template>
            <template #conversationError="{ row }">
              <span class="error-text">{{ row.errorMessage || '—' }}</span>
            </template>
          </HifyTable>
        </el-tab-pane>

        <el-tab-pane label="工作流记录" name="workflow">
          <HifyTable :columns="workflowColumns" :api="getWorkflowRuns" pagination-mode="cursor" row-key="id"
            empty-text="还没有工作流记录">
            <template #workflowStatus="{ row }">
              <el-tag :type="STATUS_TAGS[row.status as RunStatus] ?? 'info'" effect="light" size="small">
                {{ STATUS_LABELS[row.status as RunStatus] ?? row.status }}
              </el-tag>
            </template>
            <template #workflowError="{ row }">
              <span class="error-text">{{ row.errorMessage || '—' }}</span>
            </template>
          </HifyTable>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.error-text {
  color: var(--color-danger, #d9534f);
  font-size: var(--hify-font-size-sm, 12px);
}
</style>
