<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import PendingPanel from '@/components/PendingPanel.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="知识库"
      description="上传文档、固定分块、向量化，供 Agent 检索增强。"
    >
      <template #actions>
        <el-tag type="info" size="small">接口待实现</el-tag>
      </template>
    </PageHeader>

    <PendingPanel
      description="知识库与文档的接口尚未实现"
      hint="后端归属 hify-knowledge 模块。文档元数据在 MySQL（kb_document），分段正文与向量在 PostgreSQL（vec_chunk），两边靠 kb_id / doc_id 逻辑关联，没有外键。"
    />
  </div>
</template>
