import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppShell from '@/components/common/AppShell.vue'
import { setSessionExpiredHandler } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    public?: boolean
    requiresAuth?: boolean
    requiresAdmin?: boolean
    wide?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/pages/LoginPage.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/pages/RegisterPage.vue'),
    meta: { title: '注册', public: true },
  },
  {
    path: '/',
    component: AppShell,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/pages/DashboardPage.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'resumes',
        name: 'resumes',
        component: () => import('@/pages/ResumePage.vue'),
        meta: { title: '简历管理' },
      },
      {
        path: 'jds',
        name: 'jds',
        component: () => import('@/pages/JdPage.vue'),
        meta: { title: '目标岗位' },
      },
      {
        path: 'interview/new',
        name: 'interview-setup',
        component: () => import('@/pages/InterviewSetupPage.vue'),
        meta: { title: '创建模拟面试' },
      },
      {
        path: 'interview/:id',
        name: 'interview-room',
        component: () => import('@/pages/InterviewRoomPage.vue'),
        meta: { title: '模拟面试', wide: true },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/pages/ReportListPage.vue'),
        meta: { title: '面试报告' },
      },
      {
        path: 'reports/:id',
        name: 'report-detail',
        component: () => import('@/pages/ReportDetailPage.vue'),
        meta: { title: '报告详情', wide: true },
      },
      {
        path: 'plans',
        name: 'plans',
        component: () => import('@/pages/PlansPage.vue'),
        meta: { title: '套餐' },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/pages/SettingsPage.vue'),
        meta: { title: '设置' },
      },
      {
        path: 'admin',
        name: 'admin',
        component: () => import('@/pages/AdminPage.vue'),
        meta: { title: '管理后台', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/pages/NotFoundPage.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) {
      const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      return { el: to.hash, behavior: reduceMotion ? 'auto' : 'smooth' }
    }
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const store = useAppStore()
  if (!store.restoreAttempted) await store.ensureSession()
  if (to.meta.requiresAuth && !store.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin) {
    if (!store.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (store.user?.role !== 'ADMIN') {
      useToast().toast('无访问权限', 'warning', '管理后台仅对管理员开放，已返回工作台。')
      return { name: 'dashboard' }
    }
  }
  if (to.meta.public && store.isAuthenticated) {
    return { name: 'dashboard' }
  }
  return true
})

/** 刷新失败时清空内存会话并回到登录页（保留回跳地址）。 */
setSessionExpiredHandler(() => {
  const store = useAppStore()
  store.clearSession()
  const current = router.currentRoute.value
  if (current.name === 'login') return
  void router.push({ name: 'login', query: { redirect: current.fullPath } })
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · OfferCoach` : 'OfferCoach · 模拟面试'
})

export default router
