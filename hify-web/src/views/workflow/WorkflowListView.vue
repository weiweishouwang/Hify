<script setup lang="ts">
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <PendingPanel
    title="工作流"
    description="工作流定义的存取与执行接口尚未实现"
    hint="后端归属 hify-workflow 模块。工作流只支持 JSON 配置，节点六种：LLM / Agent / 工具 / 知识检索 / If-Else / 输出——没有可视化画布。"
  />
</template>
