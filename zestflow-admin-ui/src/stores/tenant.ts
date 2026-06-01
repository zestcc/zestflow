import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TenantSimpleVO } from '@/api/tenant'
import { tenantApi } from '@/api/tenant'

export const useTenantStore = defineStore('tenant', () => {
  const tenants = ref<TenantSimpleVO[]>([])
  const currentTenantId = ref<number | null>(null)

  /** 当前选中的租户详情 */
  const currentTenant = computed(() =>
    tenants.value.find(t => t.id === currentTenantId.value) || null
  )

  /** 初始化租户列表（登录成功后调用） */
  function initFromLogin(data: { tenants?: TenantSimpleVO[]; currentTenant?: TenantSimpleVO }) {
    tenants.value = data.tenants || []
    if (data.currentTenant) {
      currentTenantId.value = data.currentTenant.id
    } else if (tenants.value.length > 0) {
      currentTenantId.value = tenants.value[0].id
    }
  }

  /** 初始化：从 localStorage 恢复租户 */
  function initFromStorage() {
    const storedId = localStorage.getItem('currentTenantId')
    if (storedId) {
      currentTenantId.value = Number(storedId)
    }
  }

  /** 同步 currentTenantId 到 localStorage */
  function persistTenantId() {
    if (currentTenantId.value) {
      localStorage.setItem('currentTenantId', String(currentTenantId.value))
    } else {
      localStorage.removeItem('currentTenantId')
    }
  }

  /** 切换租户：调用后端 API 获取新 JWT */
  async function switchTenant(tenantId: number) {
    const res: any = await tenantApi.switchTenant(tenantId)
    // 更新 token
    if (res.token) {
      localStorage.setItem('token', res.token)
    }
    currentTenantId.value = tenantId
    persistTenantId()
    return res
  }

  /** 清理租户状态（退出登录时调用） */
  function clear() {
    tenants.value = []
    currentTenantId.value = null
    localStorage.removeItem('currentTenantId')
  }

  return {
    tenants, currentTenantId, currentTenant,
    initFromLogin, initFromStorage, switchTenant, persistTenantId, clear,
  }
})
