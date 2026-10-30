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
    <el-aside v-if="showChrome" width="var(--hify-sidebar-width)" class="app__aside">
      <div class="app__brand">
        <span class="app__brand-mark" />
        <span>Hify</span>
      </div>
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

/* ── 侧边栏：整个界面唯一的深色表面，视觉锚点在这 ───────────── */
.app__aside {
  background: var(--hify-bg-inverse);
  border-right: 1px solid var(--hify-border-inverse);
  display: flex;
  flex-direction: column;
}

.app__brand {
  height: var(--hify-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 var(--hify-space-4);
  font-size: var(--hify-text-lg);
  font-weight: var(--hify-weight-semibold);
  color: var(--hify-text-inverse);
  letter-spacing: var(--hify-tracking-tight);
}

/* 品牌标记：整个界面"重"的一处，用主色渐变 + 辉光 */
.app__brand-mark {
  width: 22px;
  height: 22px;
  border-radius: var(--hify-radius-sm);
  background: linear-gradient(135deg, var(--hify-primary-400), var(--hify-primary-600));
  box-shadow: var(--hify-glow-primary);
}

.app__menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--hify-text-inverse-secondary);
  --el-menu-hover-bg-color: var(--hify-bg-inverse-hover);
  --el-menu-active-color: #ffffff;
  --el-menu-item-height: 38px;
  --el-menu-base-level-padding: 12px;
  --el-menu-item-font-size: var(--hify-text-sm);
}

.app__menu :deep(.el-menu-item) {
  height: 38px;
  line-height: 38px;
  margin: 2px 10px;
  border-radius: var(--hify-radius-sm);
  transition: var(--hify-transition-color);
}

.app__menu :deep(.el-menu-item:hover) {
  background: var(--hify-bg-inverse-hover);
  color: var(--hify-text-inverse);
}

/* 选中项：深底 + 主色竖条，是侧边栏里唯一的亮色 */
.app__menu :deep(.el-menu-item.is-active) {
  background: var(--hify-bg-inverse-active);
  color: #ffffff;
  font-weight: var(--hify-weight-medium);
}

.app__menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 0 var(--hify-radius-xs) var(--hify-radius-xs) 0;
  background: var(--hify-primary-400);
}

/* ── 顶栏 ──────────────────────────────────────────────────── */
.app__header {
  display: flex;
  align-items: center;
  gap: var(--hify-space-3);
  padding: 0 var(--hify-space-5);
  background: var(--hify-bg-surface);
  border-bottom: 1px solid var(--hify-border);
}

.app__title {
  font-size: var(--hify-text-sm);
  font-weight: var(--hify-weight-semibold);
  color: var(--hify-text-primary);
}

.app__spacer {
  flex: 1;
}

.app__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--hify-text-secondary);
  outline: none;
}

.app__main {
  display: flex;
  flex-direction: column;
  padding: var(--hify-content-padding);
  overflow: auto;
}

/* 独立页面（登录、404）不要内边距，由 BlankLayout 负责居中 */
.app__main--flush {
  padding: 0;
}
</style>
