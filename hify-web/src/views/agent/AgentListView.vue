<script setup lang="ts">
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <PendingPanel
    title="Agent 配置"
    description="Agent 配置的存取与组装接口尚未实现"
    hint="后端归属 hify-agent 模块。Agent 只负责把配置组装成可运行的 Agent，不执行推理、不调用工具——工具循环在 hify-conversation 里。"
  />
</template>
