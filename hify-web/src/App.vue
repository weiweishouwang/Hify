<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ChatDotRound,
  Expand,
  Fold,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { NARROW_MEDIA, useMediaQuery } from '@/composables/useMediaQuery'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

/** 窄屏（< 1200px）：侧边栏自动折叠成只显示图标。 */
const isNarrow = useMediaQuery(NARROW_MEDIA)

/**
 * 用户手动折叠的状态。
 *
 * `null` 表示「跟随屏幕宽度」：窄屏自动折叠、宽屏自动展开。
 * 用户手动点过折叠开关之后就不再跟随；宽度跨过断点时重置回跟随。
 */
const manualCollapsed = ref<boolean | null>(null)

const collapsed = computed(() => manualCollapsed.value ?? isNarrow.value)

watch(isNarrow, () => {
  manualCollapsed.value = null
})

function toggleCollapsed(): void {
  manualCollapsed.value = !collapsed.value
}

/**
 * 侧边栏菜单：三项，与 router/index.ts 里的三条业务路由一一对应。
 * 加一项菜单要同时加一条路由，两处都要改。
 */
const menus = [
  { path: '/models', title: '模型管理', icon: Setting },
  { path: '/agents', title: 'Agent 管理', icon: User },
  { path: '/chat', title: '对话', icon: ChatDotRound },
]

/** 独立页面（登录页、404）不带侧边栏与顶栏。 */
const showChrome = computed(() => route.meta.hideLayout !== true)

/**
 * 面包屑：首页 + 当前路由链上有标题的各级。
 * 路由是扁平的，现在只有一级；以后套了层级路由，这里会自动多出几级。
 */
const breadcrumbs = computed(() => {
  const trail = route.matched.filter((item) => Boolean(item.meta?.title))
  return [
    { title: '首页', path: '/chat' },
    ...trail.map((item) => ({ title: item.meta.title as string, path: item.path })),
  ]
})

/** 头像里的字：取用户名首字。 */
const userInitial = computed(() => user.displayName.slice(0, 1).toUpperCase())

const appVersion = __APP_VERSION__

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
    <el-aside
      v-if="showChrome"
      :width="collapsed ? 'var(--hify-sidebar-width-collapsed)' : 'var(--hify-sidebar-width)'"
      class="app__aside"
      :class="{ 'app__aside--collapsed': collapsed }"
    >
      <!-- Logo 区：主色渐变文字 + 一行英文副标题 -->
      <div class="app__brand">
        <div class="app__brand-text">
          <span class="app__brand-name">Hify</span>
          <span class="app__brand-sub">AI Agent Platform</span>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        router
        class="app__menu"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>
            <span>{{ item.title }}</span>
          </template>
        </el-menu-item>
      </el-menu>

      <!-- 底部：折叠开关 + 版本号 -->
      <div class="app__footer">
        <button
          type="button"
          class="app__collapse"
          :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
          @click="toggleCollapsed"
        >
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
          <span v-if="!collapsed">收起</span>
        </button>
        <span v-if="!collapsed" class="app__version">v{{ appVersion }}</span>
      </div>
    </el-aside>

    <!-- ── 右侧内容区 ─────────────────────────────────────────── -->
    <el-container class="app__body">
      <el-header v-if="showChrome" height="var(--hify-header-height)" class="app__header">
        <!-- 左侧面包屑：当前页面路径 -->
        <el-breadcrumb separator="/">
          <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path">
            <span v-if="index === breadcrumbs.length - 1" class="app__crumb-current">
              {{ item.title }}
            </span>
            <a v-else class="app__crumb-link" @click="router.push(item.path)">{{ item.title }}</a>
          </el-breadcrumb-item>
        </el-breadcrumb>

        <div class="app__spacer" />

        <!-- 右侧用户信息 -->
        <el-dropdown @command="onCommand">
          <div class="app__user">
            <el-avatar :size="26" class="app__avatar">{{ userInitial }}</el-avatar>
            <span class="app__user-name">{{ user.displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
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
  display: flex;
  flex-direction: column;
  background: var(--color-bg-dark);
  border-right: 1px solid var(--color-border-dark);
  overflow: hidden;
}

/* Logo 区 */
.app__brand {
  display: flex;
  align-items: center;
  height: var(--hify-header-height);
  padding: 0 var(--hify-space-4);
  border-bottom: 1px solid var(--color-border-dark-subtle);
  overflow: hidden;
}

.app__brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

/* 品牌名：主色渐变文字，深底上读得出"发光" */
.app__brand-name {
  font-size: var(--hify-font-size-lg);
  font-weight: var(--hify-weight-bold);
  letter-spacing: var(--hify-tracking-tight);
  line-height: 1.2;
  background: var(--color-primary-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  white-space: nowrap;
}

.app__brand-sub {
  font-size: 10px;
  line-height: 1.2;
  letter-spacing: 0.09em;
  color: var(--color-text-dark-tertiary);
  white-space: nowrap;
}

.app__aside--collapsed .app__brand {
  padding: 0;
  justify-content: center;
}

.app__aside--collapsed .app__brand-sub {
  display: none;
}

/* ── 菜单 ────────────────────────────────────────────────────── */
.app__menu {
  flex: 1;
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--color-text-dark);
  --el-menu-hover-bg-color: var(--color-bg-dark-hover);
  --el-menu-active-color: var(--color-text-dark);
  --el-menu-item-height: 40px;
  --el-menu-base-level-padding: 14px;
  --el-menu-item-font-size: var(--hify-font-size-sm);
  --el-menu-icon-width: 18px;
}

.app__menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 10px;
  border-radius: var(--hify-radius-sm);
  color: var(--color-text-dark);
  transition: var(--hify-transition-color);
}

.app__menu :deep(.el-menu-item:hover) {
  background: var(--color-bg-dark-hover);
  color: var(--color-text-dark);
}

/* 选中态：左边一条 3px 主色竖线 + 背景微亮 */
.app__menu :deep(.el-menu-item.is-active) {
  background: var(--color-bg-dark-active);
  color: var(--color-text-dark);
  font-weight: var(--hify-weight-medium);
}

.app__menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 var(--hify-radius-xs) var(--hify-radius-xs) 0;
  background: var(--color-primary-400);
}

/* ── 侧边栏底部 ──────────────────────────────────────────────── */
.app__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--hify-space-2);
  padding: var(--hify-space-3) var(--hify-space-4);
  border-top: 1px solid var(--color-border-dark-subtle);
}

.app__collapse {
  display: inline-flex;
  align-items: center;
  gap: var(--hify-space-2);
  padding: 5px 8px;
  border: none;
  border-radius: var(--hify-radius-sm);
  background: transparent;
  color: var(--color-text-dark-secondary);
  font-family: inherit;
  font-size: var(--hify-font-size-xs);
  cursor: pointer;
  transition: var(--hify-transition-color);
}

.app__collapse:hover {
  background: var(--color-bg-dark-hover);
  color: var(--color-text-dark);
}

.app__version {
  font-family: var(--hify-font-mono);
  font-size: 11px;
  color: var(--color-text-dark-tertiary);
}

.app__aside--collapsed .app__footer {
  justify-content: center;
  padding: var(--hify-space-3) 0;
}

/* ── 顶栏 ────────────────────────────────────────────────────── */
.app__header {
  display: flex;
  align-items: center;
  gap: var(--hify-element-gap);
  padding: 0 var(--hify-page-padding);
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
}

.app__crumb-link {
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: var(--hify-transition-color);
}

.app__crumb-link:hover {
  color: var(--color-primary-600);
}

.app__crumb-current {
  color: var(--color-text-primary);
  font-weight: var(--hify-weight-medium);
}

.app__spacer {
  flex: 1;
}

.app__user {
  display: inline-flex;
  align-items: center;
  gap: var(--hify-space-2);
  cursor: pointer;
  outline: none;
}

.app__avatar {
  background: var(--color-primary-gradient);
  color: #ffffff;
  font-size: 12px;
  font-weight: var(--hify-weight-medium);
}

.app__user-name {
  font-size: var(--hify-font-size-sm);
  color: var(--color-text-secondary);
}

/* ── 内容区 ──────────────────────────────────────────────────── */
.app__main {
  padding: 0;
  background: var(--color-bg-secondary);
  overflow: auto;
}

/* 独立页面（登录、404）由 BlankLayout 负责居中，不要画布底色 */
.app__main--flush {
  padding: 0;
  background: var(--color-bg-secondary);
}
</style>
