<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
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

async function checkHealth(): Promise<void> {
  health.value = 'checking'
  try {
    healthDetail.value = await getHealth()
    health.value = 'ok'
  } catch {
    health.value = 'down'
  }
}

onMounted(checkHealth)
</script>

<template>
  <div class="page">
    <PageHeader
      title="模型管理"
      description="接入 OpenAI 兼容端点、Anthropic 与 Gemini 三套协议，维护模型接入点与可用模型。"
    >
      <template #actions>
        <el-tag :type="healthType" effect="light">{{ healthText }}</el-tag>
        <el-button @click="checkHealth">刷新状态</el-button>
      </template>
    </PageHeader>

    <div class="page-card">
      <el-empty description="模型接入点的增删改查接口尚未实现">
        <p class="hint">
          后端归属 hify-ai 模块。模型接入点与模型分两张表——连通性测试的粒度决定表的粒度：
          端点能通不代表某个模型名在该账号下可用。
        </p>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.hint {
  max-width: 520px;
  margin: 0 auto;
  color: var(--color-text-secondary);
  font-size: var(--hify-font-size-sm);
  line-height: 1.7;
}
</style>
