import http from './index'

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
  email: string
}

export interface UserVO {
  id: number
  username: string
  email: string
  avatar?: string
  isSuperAdmin?: number
  mustChangePassword?: number
}

export interface UpdateProfileDTO {
  username: string
  email: string
}

export interface UpdatePasswordDTO {
  oldPassword: string
  newPassword: string
}

export const authApi = {
  getSsoConfig() {
    return http.get<{ enabled: boolean; provider?: string; displayName?: string; issuer: string; clientId: string }>(
      '/auth/sso/config'
    )
  },

  getSsoAuthorize() {
    return http.get<{ authorizationUrl: string; state: string }>('/auth/sso/authorize')
  },

  ssoCallback(data: { code: string; state: string }) {
    return http.post<{ token: string; user: UserVO; tenants?: any[]; currentTenant?: any }>(
      '/auth/sso/callback',
      data
    )
  },

  getSsoLogoutUrl() {
    return http.get<string | null>('/auth/sso/logout-url')
  },

  login(data: LoginDTO) {
    return http.post<{ token: string; user: UserVO }>('/auth/login', data)
  },

  register(data: RegisterDTO) {
    return http.post<{ token: string; user: UserVO }>('/auth/register', data)
  },

  forgot(email: string) {
    return http.post('/auth/forgot', { email })
  },

  resetPassword(data: { token: string; password: string }) {
    return http.post('/auth/reset-password', data)
  },

  verifyEmail(token: string) {
    return http.get('/auth/verify-email', { params: { token } })
  },

  getUserInfo() {
    return http.get<UserVO>('/auth/userinfo')
  },

  logout() {
    return http.post('/auth/logout')
  },

  updateProfile(data: UpdateProfileDTO) {
    return http.put<UserVO>('/auth/profile', data)
  },

  updatePassword(data: UpdatePasswordDTO) {
    return http.put<void>('/auth/password', data)
  },

  forceChangePassword(newPassword: string) {
    return http.put<void>('/auth/force-password', { newPassword })
  },

  uploadAvatar(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<string>('/auth/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
