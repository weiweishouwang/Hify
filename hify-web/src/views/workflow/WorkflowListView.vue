<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="工作流"
      description="用 JSON 配置编排：LLM、Agent、工具、知识检索、If-Else、输出六种节点。"
    >
      <template #actions>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </template>
    </PageHeader>

    <PendingPanel
      description="工作流定义的存取与执行接口尚未实现"
      hint="后端归属 hify-workflow 模块。工作流只支持 JSON 配置，节点六种：LLM / Agent / 工具 / 知识检索 / If-Else / 输出——没有可视化画布。"
    />
  </div>
</template>
