<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { getSettings } from '@/api/settings'
import type { Settings } from '@/types/settings'

/**
 * 系统设置页（只读）。
 *
 * 回答三个问题：跑的是哪个版本、依赖通不通、关键的运行参数是多少。
 * 一期没有需要用户在页面上改的配置 —— 认证方案还没定，也没有多环境切换。
 */
const settings = ref<Settings | null>(null)
const loading = ref(false)
const failed = ref(false)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    settings.value = await getSettings()
  } catch {
    // 失败由统一请求层弹提示，这里只标记状态
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)

function formatUptime(seconds?: number): string {
  if (seconds === undefined || seconds === null) return '—'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  return `${minutes} 分钟`
}

function formatTime(value?: string): string {
  if (!value) return '—'
  const time = new Date(value)
  return Number.isNaN(time.getTime()) ? value : time.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="page">
    <PageHeader title="设置" description="版本、依赖状态与关键运行参数。">
      <template #actions>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </template>
    </PageHeader>

    <div class="page-card" v-loading="loading">
      <el-alert v-if="failed" type="error" show-icon :closable="false"
        title="读取设置失败" description="后端没有返回系统信息，检查服务是否正常。" class="settings-alert" />

      <template v-if="settings">
        <section class="settings-section">
          <h3 class="settings-title">应用信息</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="版本">{{ settings.app.version }}</el-descriptions-item>
            <el-descriptions-item label="构建时间">{{ formatTime(settings.app.buildTime) }}</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ formatTime(settings.app.startedAt) }}</el-descriptions-item>
            <el-descriptions-item label="已运行">{{ formatUptime(settings.app.uptimeSeconds) }}</el-descriptions-item>
            <el-descriptions-item label="Java 版本">{{ settings.app.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="Profile">{{ settings.app.profile }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="settings-section">
          <h3 class="settings-title">依赖状态</h3>
          <div class="dependency-list">
            <div v-for="dependency in settings.dependencies" :key="dependency.name" class="dependency-item">
              <span class="dependency-name">{{ dependency.name }}</span>
              <el-tag :type="dependency.up ? 'success' : 'danger'" effect="light" size="small">
                {{ dependency.up ? '正常' : '不可用' }}
              </el-tag>
              <span class="dependency-detail mono">{{ dependency.detail || '—' }}</span>
            </div>
          </div>
        </section>

        <section class="settings-section">
          <h3 class="settings-title">关键运行参数</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item v-for="(value, name) in settings.parameters" :key="name" :label="name">
              {{ value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.settings-alert { margin-bottom: var(--hify-space-4); }
.settings-section + .settings-section { margin-top: var(--hify-space-6); }
.settings-title {
  margin: 0 0 var(--hify-space-3);
  font-size: var(--hify-font-size-md, 14px);
  font-weight: 600;
  color: var(--color-text, #303133);
}
.dependency-list { display: flex; flex-wrap: wrap; gap: var(--hify-space-4); }
.dependency-item {
  display: flex;
  align-items: center;
  gap: var(--hify-space-2);
  padding: var(--hify-space-2) var(--hify-space-3);
  border: 1px solid var(--color-border, #dcdfe6);
  border-radius: var(--hify-radius-sm, 4px);
}
.dependency-name { font-weight: 500; }
.dependency-detail { color: var(--color-text-secondary, #909399); font-size: var(--hify-font-size-sm, 12px); }
</style>
