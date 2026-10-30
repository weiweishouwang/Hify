import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，显示在顶栏与浏览器标签页。 */
    title?: string
    /** 是否需要登录令牌。 */
    requiresAuth?: boolean
    /** 独立页面：不带侧边栏与顶栏（登录页、404）。 */
    hideLayout?: boolean
  }
}

/**
 * 路由表。
 *
 * App.vue 本身就是布局（左菜单 + 右内容区），所以这里全是顶层路由，
 * 不套一层带 children 的布局路由 —— 套了会出现「App.vue 渲染 App.vue」的递归。
 *
 * 侧边栏菜单在 App.vue 里，目前三项：模型管理 / Agent 管理 / 对话。
 * 下面「知识库 / 工作流 / 日志」三条是占位路由，功能还没做，所以没进菜单，
 * 做完之后把 App.vue 的 menus 补上即可。
 */
export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/chat' },
  {
    path: '/models',
    name: 'models',
    component: () => import('@/views/model/ProviderList.vue'),
    meta: { title: '模型管理', requiresAuth: true },
  },
  {
    path: '/agents',
    name: 'agents',
    component: () => import('@/views/agent/AgentListView.vue'),
    meta: { title: 'Agent 管理', requiresAuth: true },
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/chat/ChatView.vue'),
    meta: { title: '对话', requiresAuth: true },
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: () => import('@/views/knowledge/KnowledgeListView.vue'),
    meta: { title: '知识库', requiresAuth: true },
  },
  {
    path: '/workflows',
    name: 'workflows',
    component: () => import('@/views/workflow/WorkflowListView.vue'),
    meta: { title: '工作流', requiresAuth: true },
  },
  {
    path: '/logs',
    name: 'logs',
    component: () => import('@/views/log/LogListView.vue'),
    meta: { title: '日志', requiresAuth: true },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', hideLayout: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', hideLayout: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/**
 * 路由守卫。
 *
 * 认证方案还没定（见 CLAUDE.md §五），这里只做「有没有令牌」这一层判断：
 * 令牌在 localStorage，刷新后仍在，所以守卫不依赖内存状态。
 */
router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.requiresAuth && !user.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && user.isAuthenticated) {
    return { name: 'chat' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · Hify` : 'Hify'
})

export default router
