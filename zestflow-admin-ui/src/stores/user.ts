import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginDTO, RegisterDTO, UpdateProfileDTO, UserVO } from '@/api/auth'
import { authApi } from '@/api/auth'
import { useTenantStore } from '@/stores/tenant'
import { useAppStore } from '@/stores/app'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)
  const mustChangePassword = ref(false)

  async function login(data: LoginDTO) {
    const res: any = await authApi.login(data)
    applyLoginResult(res)
  }

  async function loginBySso(data: { code: string; state: string }) {
    const res: any = await authApi.ssoCallback(data)
    applyLoginResult(res, true)
  }

  function applyLoginResult(res: any, viaSso = false) {
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
    if (viaSso) {
      localStorage.setItem('sso_login', 'true')
    }
    mustChangePassword.value = res.user?.mustChangePassword === 1
    if (res.tenants || res.currentTenant) {
      const tenantStore = useTenantStore()
      tenantStore.initFromLogin(res)
      tenantStore.persistTenantId()
    }
  }

  async function register(data: RegisterDTO) {
    const res: any = await authApi.register(data)
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
    mustChangePassword.value = false
  }

  async function getUserInfo() {
    try {
      const res: any = await authApi.getUserInfo()
      user.value = res
      mustChangePassword.value = res.mustChangePassword === 1
    } catch {
      logout()
    }
  }

  async function updateProfile(data: UpdateProfileDTO) {
    const res: any = await authApi.updateProfile(data)
    user.value = res
    return res
  }

  function clearMustChangePassword() {
    mustChangePassword.value = false
    if (user.value) {
      user.value.mustChangePassword = 0
    }
  }

  async function logout() {
    const viaSso = localStorage.getItem('sso_login') === 'true'
    token.value = ''
    user.value = null
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('sso_login')
    useAppStore().clearCurrentAppCode()
    const tenantStore = useTenantStore()
    tenantStore.clear()
    if (viaSso) {
      try {
        const logoutUrl = await authApi.getSsoLogoutUrl()
        if (logoutUrl) {
          window.location.href = logoutUrl
          return
        }
      } catch {
        // 降级为本地登出
      }
    }
    router.push({ name: 'Login' })
  }

  return {
    token, user, mustChangePassword,
    login, loginBySso, register, getUserInfo, updateProfile,
    clearMustChangePassword, logout,
  }
})
