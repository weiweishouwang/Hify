<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getHealth } from '@/api/health'

/** 后端连通状态：checking 探测中 / ok 已连接 / down 未连接。 */
type HealthState = 'checking' | 'ok' | 'down'

const health = ref<HealthState>('checking')

/** 后端返回的状态文案，成功时拼进「后端已连接：…」。 */
const healthDetail = ref('')

const healthText = computed(() => {
  switch (health.value) {
    case 'ok':
      return `后端已连接：${healthDetail.value}`
    case 'down':
      return '后端未连接'
    default:
      return '检查中…'
  }
})

/** 标签颜色：绿 / 红 / 灰。 */
const healthType = computed<'success' | 'danger' | 'info'>(() => {
  switch (health.value) {
    case 'ok':
      return 'success'
    case 'down':
      return 'danger'
    default:
      return 'info'
  }
})

onMounted(async () => {
  try {
    healthDetail.value = await getHealth()
    health.value = 'ok'
  } catch {
    health.value = 'down'
  }
})
</script>

<template>
  <div>
    <div class="page-title">模型提供商管理</div>

    <el-tag :type="healthType" class="health">{{ healthText }}</el-tag>
  </div>
</template>

<style scoped>
.health {
  margin-top: 12px;
}
</style>
