import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/RegisterPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/forgot',
    name: 'Forgot',
    component: () => import('@/views/forgot/ForgotPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/force-password',
    name: 'ForcePassword',
    component: () => import('@/views/login/ForceChangePassword.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/',
    component: () => import('@/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardPage.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'chains',
        name: 'Chains',
        component: () => import('@/views/chains/ChainsPage.vue'),
        meta: { title: '链管理' },
      },
      {
        path: 'chains/create',
        name: 'ChainCreate',
        component: () => import('@/views/chains/ChainCreatePage.vue'),
        meta: { title: '新建链' },
      },
      {
        path: 'chains/:id',
        name: 'ChainDetail',
        component: () => import('@/views/chains/ChainDetailPage.vue'),
        meta: { title: '链详情' },
      },
      {
        path: 'design',
        name: 'DesignList',
        component: () => import('@/views/design/DesignListPage.vue'),
        meta: { title: '设计列表' },
      },
      {
        path: 'design/:id',
        name: 'DesignEditor',
        component: () => import('@/views/design/DesignEditorPage.vue'),
        meta: { title: '设计编辑器' },
      },
      {
        path: 'components',
        name: 'Components',
        component: () => import('@/views/components/ComponentsPage.vue'),
        meta: { title: '元件列表' },
      },
      {
        path: 'schedules',
        name: 'Schedules',
        component: () => import('@/views/schedules/SchedulesPage.vue'),
        meta: { title: '调度中心' },
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/LogsPage.vue'),
        meta: { title: '日志查询' },
      },
      {
        path: 'executors',
        name: 'Executors',
        component: () => import('@/views/settings/ModuleManagePage.vue'),
        meta: { title: '执行器列表' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsPage.vue'),
        meta: { title: '系统设置' },
        redirect: { name: 'UserManage' },
        children: [
          {
            path: 'profile',
            name: 'Profile',
            component: () => import('@/views/profile/ProfilePage.vue'),
            meta: { title: '用户信息' },
          },
          {
            path: 'users',
            name: 'UserManage',
            component: () => import('@/views/settings/UserManagePage.vue'),
            meta: { title: '用户管理' },
          },
        ],
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 导航守卫：未登录跳转登录页
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  const isLoggedIn = !!userStore.token

  if (to.meta.requiresAuth === false) {
    // 公共页面，直接访问
    next()
  } else if (!isLoggedIn) {
    // 需要登录但未登录，跳转登录页
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else if (userStore.mustChangePassword && to.name !== 'ForcePassword') {
    // 需要强制改密，跳转到改密页
    next({ name: 'ForcePassword' })
  } else {
    next()
  }
})

export default router
