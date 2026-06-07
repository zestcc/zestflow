import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useTenantStore } from '@/stores/tenant'

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
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/reset-password/ResetPasswordPage.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/verify-email',
    name: 'VerifyEmail',
    component: () => import('@/views/verify-email/VerifyEmailPage.vue'),
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
        meta: { title: '执行链管理', requiresExecutor: true },
      },
      {
        path: 'chains/create',
        name: 'ChainCreate',
        component: () => import('@/views/chains/ChainCreatePage.vue'),
        meta: { title: '新建链', requiresExecutor: true },
      },
      {
        path: 'chains/:id',
        name: 'ChainDetail',
        component: () => import('@/views/chains/ChainDetailPage.vue'),
        meta: { title: '链详情', requiresExecutor: true },
      },
      {
        path: 'design',
        name: 'DesignList',
        component: () => import('@/views/design/DesignListPage.vue'),
        meta: { title: '设计列表', requiresExecutor: true },
      },
      {
        path: 'design/ai-templates',
        name: 'AiTemplates',
        component: () => import('@/views/design/AiTemplatesPage.vue'),
        meta: { title: 'AI 模板库', requiresExecutor: true },
      },
      {
        path: 'design/:id',
        name: 'DesignEditor',
        component: () => import('@/views/design/DesignEditorPage.vue'),
        meta: { title: '设计编辑器', hideTitle: true, requiresExecutor: true },
      },
      {
        path: 'playground/scenes',
        name: 'PlaygroundScenes',
        component: () => import('@/views/playground/scenes/PlaygroundScenesPage.vue'),
        meta: { title: '演示场景', requiresExecutor: true },
      },
      {
        path: 'playground/records',
        name: 'PlaygroundRecords',
        component: () => import('@/views/playground/records/PlaygroundRecordsPage.vue'),
        meta: { title: '演示记录', requiresExecutor: true },
      },
      {
        path: 'playground',
        name: 'Playground',
        component: () => import('@/views/playground/PlaygroundPage.vue'),
        meta: { title: '试验场', hideTitle: true, requiresExecutor: true },
      },
      {
        path: 'components',
        name: 'Components',
        component: () => import('@/views/components/ComponentsPage.vue'),
        meta: { title: '元件列表', requiresExecutor: true },
      },
      {
        path: 'schedules',
        name: 'Schedules',
        component: () => import('@/views/schedules/SchedulesPage.vue'),
        meta: { title: '调度中心', requiresExecutor: true },
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/LogsPage.vue'),
        meta: { title: '日志查询', requiresExecutor: true },
      },
      {
        path: 'executors',
        name: 'Executors',
        component: () => import('@/views/executors/ExecutorsPage.vue'),
        meta: { title: '执行器列表' },
      },
      {
        path: 'collectors',
        name: 'Collectors',
        component: () => import('@/views/collectors/CollectorsPage.vue'),
        meta: { title: '采集器列表' },
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
          {
            path: 'dict-types',
            name: 'DictTypes',
            component: () => import('@/views/settings/DictTypesPage.vue'),
            meta: { title: '字典管理' },
          },
          {
            path: 'sys-config',
            name: 'SysConfig',
            component: () => import('@/views/settings/SettingsSysConfigPage.vue'),
            meta: { title: '系统配置' },
          },
          {
            path: 'tenants',
            name: 'TenantManage',
            component: () => import('@/views/tenant/TenantManagePage.vue'),
            meta: { title: '租户管理' },
          },
          {
            path: 'ai',
            name: 'SettingsAi',
            component: () => import('@/views/settings/SettingsAiPage.vue'),
            meta: { title: 'AI 配置' },
          },
          {
            path: 'alerts',
            name: 'SettingsAlerts',
            component: () => import('@/views/settings/SettingsAlertPage.vue'),
            meta: { title: 'SLA 告警' },
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
    // 登录后初始化租户（页面刷新时从 localStorage 恢复）
    if (isLoggedIn) {
      const tenantStore = useTenantStore()
      if (!tenantStore.currentTenantId && localStorage.getItem('currentTenantId')) {
        tenantStore.initFromStorage()
      }
    }
    next()
  }
})

export default router
