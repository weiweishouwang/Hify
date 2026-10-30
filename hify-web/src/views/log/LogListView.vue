<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="日志"
      description="查看每轮对话的运行记录：调用了什么模型、耗了多少 token、工具调用过程。"
    >
      <template #actions>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </template>
    </PageHeader>

    <PendingPanel
      description="调用日志的查询接口尚未实现"
      hint="对话的每一轮运行都会落到 conv_run 与 conv_message 两张分区表，按 created_at 做 RANGE 分区，查询走游标分页而不是 OFFSET。"
    />
  </div>
</template>
