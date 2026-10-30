<script setup lang="ts">
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <PendingPanel
    title="日志"
    description="调用日志的查询接口尚未实现"
    hint="对话的每一轮运行都会落到 conv_run 与 conv_message 两张分区表，按 created_at 做 RANGE 分区，查询走游标分页而不是 OFFSET。"
  />
</template>
