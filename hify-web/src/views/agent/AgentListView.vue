<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="Agent 管理"
      description="配置 Agent：选模型、绑工具、设提示词，可选绑知识库。"
    >
      <template #actions>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </template>
    </PageHeader>

    <PendingPanel
      description="Agent 的增删改查与版本发布接口尚未实现"
      hint="后端归属 hify-agent 模块。Agent 只负责把配置组装成一个可运行的 Agent，不执行推理、不调用工具——那两件事在 hify-conversation。"
    />
  </div>
</template>
