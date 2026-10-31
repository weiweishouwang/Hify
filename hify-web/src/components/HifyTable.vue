<script lang="ts">
import type { CursorPage, CursorQuery, PageQuery } from '@/types/api'

export interface HifyTableColumn<Row> {
  label: string
  prop?: string
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  formatter?: (row: Row, value: unknown) => string
  slot?: string
  hideOnNarrow?: boolean
}

export type HifyTableApi<Row> = (params: PageQuery) => Promise<{ data: Row[]; total: number }>
export type HifyCursorTableApi<Row> = (params: CursorQuery) => Promise<CursorPage<Row>>
export interface HifyTableHandle<Row> {
  refresh: (options?: { reset?: boolean }) => Promise<void>
  toggleRowExpansion: (row: Row, expanded?: boolean) => void
}
</script>

<script setup lang="ts" generic="Row extends object">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import type { TableInstance } from 'element-plus'
import { NARROW_MEDIA, useMediaQuery } from '@/composables/useMediaQuery'

const props = withDefaults(defineProps<{
  columns: HifyTableColumn<Row>[]
  api: HifyTableApi<Row> | HifyCursorTableApi<Row>
  paginationMode?: 'page' | 'cursor'
  paginated?: boolean
  pageSize?: number
  rowKey?: string
  emptyText?: string
}>(), {
  paginationMode: 'page', paginated: true, pageSize: 20, rowKey: 'id', emptyText: '暂无数据',
})
const emit = defineEmits<{
  'expand-change': [row: Row, expanded: boolean]
  loaded: [rows: Row[]]
}>()

const rows = shallowRef<Row[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)
const page = ref(1)
const size = ref(props.pageSize)
const table = ref<TableInstance>()
const cursors = ref<Array<string | undefined>>([undefined])
const hasNext = ref(false)
const nextCursor = ref<string | undefined>()
const isNarrow = useMediaQuery(NARROW_MEDIA)
const rowStyle = { height: '52px' }
let requestSequence = 0

const visibleColumnDefs = computed(() => props.columns
  .filter(column => !isNarrow.value || !column.hideOnNarrow)
  .map(column => ({ column, formatter: toFormatter(column) })))

function toFormatter(column: HifyTableColumn<Row>) {
  const formatter = column.formatter
  return formatter ? (row: Row, _column: unknown, value: unknown) => formatter(row, value) : undefined
}

async function fetchData(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  failed.value = false
  try {
    if (props.paginationMode === 'cursor') {
      const result = await (props.api as HifyCursorTableApi<Row>)({ size: size.value, cursor: cursors.value[page.value - 1] })
      if (sequence !== requestSequence) return
      rows.value = result.items
      hasNext.value = result.hasNext
      nextCursor.value = result.nextCursor ?? undefined
    } else {
      const result = await (props.api as HifyTableApi<Row>)({ page: page.value, size: size.value })
      if (sequence !== requestSequence) return
      rows.value = result.data ?? []
      total.value = result.total ?? 0
    }
    if (rows.value.length === 0 && page.value > 1) {
      page.value -= 1
      cursors.value = cursors.value.slice(0, page.value)
      await fetchData()
      return
    }
    emit('loaded', rows.value)
  } catch {
    // 请求层已提示错误；保留旧数据并显示失败状态，不把失败伪装成“暂无数据”。
    if (sequence === requestSequence) failed.value = true
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function handlePageChange(next: number): void {
  page.value = next
  void fetchData()
}

function handleSizeChange(next: number): void {
  size.value = next
  void refresh({ reset: true })
}

function previousPage(): void {
  if (loading.value || page.value <= 1) return
  page.value -= 1
  cursors.value = cursors.value.slice(0, page.value)
  void fetchData()
}

function nextPage(): void {
  if (loading.value || !hasNext.value || !nextCursor.value) return
  cursors.value.push(nextCursor.value)
  page.value += 1
  void fetchData()
}

function refresh(options?: { reset?: boolean }): Promise<void> {
  if (options?.reset) {
    page.value = 1
    cursors.value = [undefined]
    nextCursor.value = undefined
    hasNext.value = false
  }
  return fetchData()
}

function toggleRowExpansion(row: Row, expanded?: boolean): void {
  table.value?.toggleRowExpansion(row, expanded)
}

function handleExpandChange(row: Row, expanded: Row[] | boolean): void {
  emit('expand-change', row, Array.isArray(expanded) ? expanded.includes(row) : expanded)
}

onMounted(fetchData)
onBeforeUnmount(() => { requestSequence += 1 })
defineExpose({ refresh, toggleRowExpansion, rows, total, loading, page, size })
</script>

<template>
  <div class="hify-table">
    <div v-if="failed" class="hify-table__error" role="alert">
      <span>数据加载失败，请重试</span>
      <el-button text type="primary" :loading="loading" @click="refresh()">重试</el-button>
    </div>
    <el-table
      ref="table"
      v-loading="loading"
      :data="rows"
      :row-key="rowKey"
      :row-style="rowStyle"
      stripe
      class="hify-table__table"
      @expand-change="handleExpandChange"
    >
      <el-table-column v-if="$slots.expand" type="expand" width="48">
        <template #default="scope">
          <slot name="expand" :row="scope.row as Row" />
        </template>
      </el-table-column>
      <el-table-column
        v-for="def in visibleColumnDefs"
        :key="def.column.label"
        :label="def.column.label"
        :prop="def.column.prop"
        :width="def.column.width"
        :min-width="def.column.minWidth"
        :align="def.column.align ?? 'left'"
        :fixed="def.column.fixed"
        :formatter="def.formatter"
        show-overflow-tooltip
      >
        <template v-if="def.column.slot" #default="scope">
          <slot :name="def.column.slot" :row="scope.row as Row" :index="scope.$index"
            :value="(scope.row as Record<string, unknown>)[def.column.prop ?? '']" />
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="failed ? '暂时无法加载数据' : emptyText" :image-size="72" />
      </template>
    </el-table>

    <div v-if="paginated" class="hify-table__pager">
      <div v-if="paginationMode === 'cursor'" class="hify-table__cursor">
        <el-select v-model="size" aria-label="每页条数" class="hify-table__size" :disabled="loading" @change="handleSizeChange">
          <el-option v-for="option in [10, 20, 50, 100, 200]" :key="option" :label="`${option} 条/页`" :value="option" />
        </el-select>
        <el-button :disabled="loading || page <= 1" @click="previousPage">上一页</el-button>
        <el-button :disabled="loading || failed || !hasNext || !nextCursor" @click="nextPage">下一页</el-button>
      </div>
      <el-pagination v-else v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" background
        @current-change="handlePageChange" @size-change="handleSizeChange" />
    </div>
  </div>
</template>

<style scoped>
.hify-table { display: flex; flex-direction: column; gap: var(--hify-element-gap); }
.hify-table__table { width: 100%; }
.hify-table__pager { display: flex; justify-content: flex-end; padding-top: var(--hify-space-4); border-top: 1px solid var(--color-border-subtle); }
.hify-table__cursor { display: flex; align-items: center; gap: var(--hify-space-2); }
.hify-table__cursor .el-button + .el-button { margin-left: 0; }
.hify-table__size { width: calc(var(--hify-space-6) * 5); }
.hify-table__error { display: flex; align-items: center; justify-content: space-between; color: var(--color-danger-600); }
</style>
