<script setup lang="ts">
import BlankLayout from '@/layouts/BlankLayout.vue'
</script>

<template>
  <BlankLayout>
    <el-result icon="warning" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/chat')">回到对话</el-button>
      </template>
    </el-result>
  </BlankLayout>
</template>
