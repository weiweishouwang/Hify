<script setup lang="ts">
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <PendingPanel
    title="模型管理"
    description="模型接入点与模型清单的 CRUD 接口尚未实现"
    hint="后端归属 hify-ai 模块（ai_model_provider / ai_model 两张表）。接入点与模型分两张表，因为连通性测试的粒度是端点，而模型名是否可用取决于账号。"
  />
</template>
