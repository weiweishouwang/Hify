<script setup lang="ts">
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <PendingPanel
    title="知识库"
    description="知识库与文档的接口尚未实现"
    hint="后端归属 hify-knowledge 模块。文档元数据在 MySQL（kb_document），分段正文与向量在 PostgreSQL（vec_chunk），两边靠 kb_id / doc_id 逻辑关联，没有外键。"
  />
</template>
