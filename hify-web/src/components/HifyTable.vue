<script lang="ts">
import type { PageQuery } from '@/types/api'

/**
 * 一列的配置。
 *
 * `prop` 走默认渲染，`slot` 交给父组件自定义，两者二选一。
 */
export interface HifyTableColumn<Row> {
  /** 表头文字。 */
  label: string
  /** 字段名。走默认渲染时必填。 */
  prop?: string
  /** 列宽。 */
  width?: number | string
  /** 最小列宽。与 width 二选一。 */
  minWidth?: number | string
  /** 对齐方式，默认左对齐。 */
  align?: 'left' | 'center' | 'right'
  /** 固定在左侧还是右侧。 */
  fixed?: boolean | 'left' | 'right'
  /**
   * 自定义渲染。签名收成「行 + 值」两参，比 Element 的四参少写两个占位。
   * 与 slot 二选一，slot 优先。
   */
  formatter?: (row: Row, value: unknown) => string
  /** 自定义插槽名；同名插槽由父组件提供。 */
  slot?: string
  /** 窄屏（< 1200px）下隐藏这一列 —— 次要列在窄屏上不占地方。 */
  hideOnNarrow?: boolean
}

/** 表格对外的接口方法签名。 */
export type HifyTableApi<Row> = (params: PageQuery) => Promise<{
  data: Row[]
  total: number
}>
</script>

<script setup lang="ts" generic="Row extends object">
import { computed, onMounted, ref } from 'vue'
import { NARROW_MEDIA, useMediaQuery } from '@/composables/useMediaQuery'

const props = withDefaults(
  defineProps<{
    /** 列配置。 */
    columns: HifyTableColumn<Row>[]
    /** 取一页数据的接口方法，返回 PageResult 结构。 */
    api: (params: PageQuery) => Promise<{ data: Row[]; total: number }>
    /** 是否显示分页。 */
    paginated?: boolean
    /** 每页条数。 */
    pageSize?: number
    /** 行主键字段。 */
    rowKey?: string
    /** 空状态文案。 */
    emptyText?: string
  }>(),
  {
    paginated: true,
    pageSize: 20,
    rowKey: 'id',
    emptyText: '暂无数据',
  },
)

const rows = ref<Row[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(props.pageSize)

/** Element 的 formatter 是四参，这里包成两参（行 + 值）。 */
function toFormatter(column: HifyTableColumn<Row>) {
  const fn = column.formatter
  if (!fn) {
    return undefined
  }
  return (row: Row, _column: unknown, value: unknown) => fn(row, value)
}

/** 每列渲染用的定义，formatter 在这里包好。 */
const columnDefs = computed(() =>
  props.columns.map((column) => ({ column, formatter: toFormatter(column) })),
)

/** 窄屏（< 1200px）：把标了 hideOnNarrow 的次要列去掉，只留关键信息。 */
const isNarrow = useMediaQuery(NARROW_MEDIA)

const visibleColumnDefs = computed(() =>
  isNarrow.value
    ? columnDefs.value.filter((def) => !def.column.hideOnNarrow)
    : columnDefs.value,
)

/** 行高。比 Element 默认高一点，表格不显拥挤。 */
const rowStyle = { height: '52px' }

async function fetchData(): Promise<void> {
  loading.value = true
  try {
    const result = await props.api({ page: page.value, size: size.value })
    rows.value = result.data ?? []
    total.value = result.total ?? 0
    // 当前页被删空了（删的是最后一页的最后一条）：往回退一页再取
    if (rows.value.length === 0 && page.value > 1) {
      page.value -= 1
      await fetchData()
    }
  } catch {
    // 错误提示已由请求层弹出，这里只把表格清空
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(next: number): void {
  page.value = next
  void fetchData()
}

function handleSizeChange(next: number): void {
  size.value = next
  page.value = 1
  void fetchData()
}

/**
 * 重新取当前页。
 *
 * 父组件在增删改之后调用它刷新列表。
 */
function refresh(): Promise<void> {
  return fetchData()
}

onMounted(fetchData)

defineExpose({ refresh, rows, total, loading, page, size })
</script>

<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="rows"
      :row-key="rowKey"
      :row-style="rowStyle"
      stripe
      class="hify-table__table"
    >
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
        <!-- 这一列要自定义渲染时，交给父组件的同名插槽 -->
        <template v-if="def.column.slot" #default="scope">
          <slot
            :name="def.column.slot"
            :row="scope.row"
            :index="scope.$index"
            :value="(scope.row as Record<string, unknown>)[def.column.prop ?? '']"
          />
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :description="emptyText" :image-size="72" />
      </template>
    </el-table>

    <div v-if="paginated" class="hify-table__pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hify-table {
  display: flex;
  flex-direction: column;
  gap: var(--hify-element-gap);
}

.hify-table__table {
  width: 100%;
}

/* 分页靠右，与内容区右边缘对齐 */
.hify-table__pager {
  display: flex;
  justify-content: flex-end;
}
</style>
