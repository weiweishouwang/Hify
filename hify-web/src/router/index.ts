import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题，显示在顶栏与浏览器标签页。 */
    title?: string
    /** 是否需要登录令牌。 */
    requiresAuth?: boolean
    /** 是否在侧边栏菜单里显示。 */
    hidden?: boolean
  }
}

/**
 * 路由表。
 *
 * 管理后台的五个页面 + 一个对话页。侧边栏菜单由 AppLayout 读这份路由表生成，
 * 加页面只改这一处。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: '', redirect: '/chat' },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: '对话', requiresAuth: true },
      },
      {
        path: 'models',
        name: 'models',
        component: () => import('@/views/model/ModelListView.vue'),
        meta: { title: '模型管理', requiresAuth: true },
      },
      {
        path: 'agents',
        name: 'agents',
        component: () => import('@/views/agent/AgentListView.vue'),
        meta: { title: 'Agent 配置', requiresAuth: true },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/knowledge/KnowledgeListView.vue'),
        meta: { title: '知识库', requiresAuth: true },
      },
      {
        path: 'workflows',
        name: 'workflows',
        component: () => import('@/views/workflow/WorkflowListView.vue'),
        meta: { title: '工作流', requiresAuth: true },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/log/LogListView.vue'),
        meta: { title: '日志', requiresAuth: true },
      },
    ],
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在', hidden: true },
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
