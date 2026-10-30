<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ChatDotRound,
  Collection,
  Cpu,
  Document,
  MagicStick,
  Share,
  SwitchButton,
} from '@element-plus/icons-vue'
import { useConnectionStore } from '@/stores/connection'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const connection = useConnectionStore()

/** 侧边栏菜单。加页面时这里加一项，路由表同步加一条。 */
const menus = [
  { path: '/chat', title: '对话', icon: ChatDotRound },
  { path: '/models', title: '模型管理', icon: Cpu },
  { path: '/agents', title: 'Agent 配置', icon: MagicStick },
  { path: '/knowledge', title: '知识库', icon: Collection },
  { path: '/workflows', title: '工作流', icon: Share },
  { path: '/logs', title: '日志', icon: Document },
]

const connectionLabels: Record<string, string> = {
  idle: '未连接',
  connecting: '连接中',
  open: '已连接',
  closed: '已断开',
  error: '连接异常',
}

const connectionText = computed(() => connectionLabels[connection.status] ?? '未连接')

const connectionTagType = computed<'info' | 'success' | 'warning' | 'danger'>(() => {
  switch (connection.status) {
    case 'open':
      return 'success'
    case 'connecting':
      return 'warning'
    case 'error':
      return 'danger'
    default:
      return 'info'
  }
})

function onCommand(command: string): void {
  if (command === 'logout') {
    user.logout()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="212px" class="layout__aside">
      <div class="layout__brand">Hify</div>
      <el-menu :default-active="route.path" router class="layout__menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header height="56px" class="layout__header">
        <span class="layout__title">{{ route.meta.title ?? 'Hify' }}</span>
        <div class="layout__spacer" />
        <el-tag v-if="connection.status !== 'idle'" :type="connectionTagType" size="small">
          {{ connectionText }}
        </el-tag>
        <el-dropdown @command="onCommand">
          <span class="layout__user">
            {{ user.displayName }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="layout__main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout__aside {
  background: var(--hify-surface);
  border-right: 1px solid var(--hify-border);
  display: flex;
  flex-direction: column;
}

.layout__brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 18px;
  font-weight: 600;
  color: var(--hify-text);
}

.layout__menu {
  border-right: none;
}

.layout__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  background: var(--hify-surface);
  border-bottom: 1px solid var(--hify-border);
}

.layout__title {
  font-size: 15px;
  font-weight: 600;
}

.layout__spacer {
  flex: 1;
}

.layout__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--hify-text-muted);
  outline: none;
}

.layout__main {
  padding: 20px;
  overflow: auto;
}
</style>
