<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ChatDotRound, Cpu, MagicStick, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

/**
 * 侧边栏菜单：三项，与 router/index.ts 里的三条业务路由一一对应。
 * 加一项菜单要同时加一条路由，两处都要改。
 */
const menus = [
  { path: '/models', title: '模型管理', icon: Cpu },
  { path: '/agents', title: 'Agent 管理', icon: MagicStick },
  { path: '/chat', title: '对话', icon: ChatDotRound },
]

/** 登录页、404 这类独立页面不带侧边栏与顶栏。 */
const showChrome = computed(() => route.meta.hideLayout !== true)

function onCommand(command: string): void {
  if (command === 'logout') {
    user.logout()
    router.push({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="app">
    <!-- ── 左侧菜单栏 ─────────────────────────────────────────── -->
    <el-aside v-if="showChrome" width="200px" class="app__aside">
      <div class="app__brand">Hify</div>
      <el-menu :default-active="route.path" router class="app__menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- ── 右侧内容区 ─────────────────────────────────────────── -->
    <el-container>
      <el-header v-if="showChrome" height="56px" class="app__header">
        <span class="app__title">{{ route.meta.title ?? 'Hify' }}</span>
        <div class="app__spacer" />
        <el-dropdown @command="onCommand">
          <span class="app__user">
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

      <el-main class="app__main" :class="{ 'app__main--flush': !showChrome }">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app {
  height: 100%;
}

.app__aside {
  background: var(--hify-surface);
  border-right: 1px solid var(--hify-border);
  display: flex;
  flex-direction: column;
}

.app__brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-size: 18px;
  font-weight: 600;
}

.app__menu {
  border-right: none;
}

.app__header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  background: var(--hify-surface);
  border-bottom: 1px solid var(--hify-border);
}

.app__title {
  font-size: 15px;
  font-weight: 600;
}

.app__spacer {
  flex: 1;
}

.app__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--hify-text-muted);
  outline: none;
}

.app__main {
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: auto;
}

/* 独立页面（登录、404）不要内边距，由 BlankLayout 负责居中 */
.app__main--flush {
  padding: 0;
}
</style>
