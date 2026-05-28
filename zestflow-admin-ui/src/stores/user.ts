import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginDTO, RegisterDTO, UpdateProfileDTO, UserVO } from '@/api/auth'
import { authApi } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserVO | null>(null)

  async function login(data: LoginDTO) {
    const res: any = await authApi.login(data)
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
  }

  async function register(data: RegisterDTO) {
    const res: any = await authApi.register(data)
    token.value = res.token
    user.value = res.user
    localStorage.setItem('token', res.token)
  }

  async function getUserInfo() {
    try {
      const res: any = await authApi.getUserInfo()
      user.value = res
    } catch {
      logout()
    }
  }

  async function updateProfile(data: UpdateProfileDTO) {
    const res: any = await authApi.updateProfile(data)
    user.value = res
    return res
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    router.push({ name: 'Login' })
  }

  return { token, user, login, register, getUserInfo, updateProfile, logout }
})
