<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="对话"
      description="与 Agent 多轮对话，流式输出，支持工具调用。"
    >
      <template #actions>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </template>
    </PageHeader>

    <div class="page-card">
      <el-empty description="对话接口尚未实现" />
    </div>
  </div>
</template>
