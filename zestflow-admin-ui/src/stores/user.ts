import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginDTO, RegisterDTO, UpdateProfileDTO, UserVO } from '@/api/auth'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)
  const mustChangePassword = ref(false)

  async function login(data: LoginDTO) {
    const res: any = await authApi.login(data)
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
    mustChangePassword.value = res.user?.mustChangePassword === 1
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

  function logout() {
    token.value = ''
    user.value = null
    mustChangePassword.value = false
    localStorage.removeItem('token')
    router.push({ name: 'Login' })
  }

  return {
    token, user, mustChangePassword,
    login, register, getUserInfo, updateProfile,
    clearMustChangePassword, logout,
  }
})
