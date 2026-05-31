import http from './index'

export interface AppRoleAssignment {
  appCode: string
  roleId: number
  roleCode: string
  roleName: string
}

export interface UserManageVO {
  id: number
  username: string
  email: string
  avatar?: string
  status: number
  isSuperAdmin: number
  mustChangePassword?: number
  generatedPassword?: string
  appRoles: AppRoleAssignment[]
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface UserCreateDTO {
  username: string
  email: string
  isSuperAdmin?: number
}

export interface UserUpdateDTO {
  username?: string
  email?: string
  status?: number
  isSuperAdmin?: number
}

export interface RoleVO {
  id: number
  code: string
  name: string
  description: string
}

export interface AssignAppRoleDTO {
  userId: number
  appCode: string
  roleId: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export const userManageApi = {
  list(params?: { username?: string; email?: string; status?: number; isSuperAdmin?: number; page?: number; size?: number }) {
    return http.get<PageResponse<UserManageVO> | UserManageVO[]>('/users', { params })
  },
  getById(id: number) {
    return http.get<UserManageVO>(`/users/${id}`)
  },
  create(data: UserCreateDTO) {
    return http.post<UserManageVO>('/users', data)
  },
  update(id: number, data: UserUpdateDTO) {
    return http.put<UserManageVO>(`/users/${id}`, data)
  },
  delete(id: number) {
    return http.delete<void>(`/users/${id}`)
  },
  resetPassword(id: number) {
    return http.put<{ generatedPassword: string }>(`/users/${id}/reset-password`)
  },
  assignAppRole(data: AssignAppRoleDTO) {
    return http.post<void>(`/users/${data.userId}/app-roles`, data)
  },
  removeAppRole(userId: number, appCode: string) {
    return http.delete<void>(`/users/${userId}/app-roles/${appCode}`)
  },
}

export const roleApi = {
  list() {
    return http.get<RoleVO[]>('/roles')
  },
}
