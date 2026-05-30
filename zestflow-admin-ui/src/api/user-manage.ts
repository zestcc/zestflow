import http from './index'

export interface ModuleRoleAssignment {
  moduleId: number
  moduleCode: string
  moduleName: string
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
  moduleRoles: ModuleRoleAssignment[]
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

export interface AssignModuleRoleDTO {
  userId: number
  moduleId: number
  roleId: number
}

export const userManageApi = {
  list() {
    return http.get<UserManageVO[]>('/users')
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
  assignModuleRole(data: AssignModuleRoleDTO) {
    return http.post<void>(`/users/${data.userId}/module-roles`, data)
  },
  removeModuleRole(userId: number, moduleId: number) {
    return http.delete<void>(`/users/${userId}/module-roles/${moduleId}`)
  },
}

export const roleApi = {
  list() {
    return http.get<RoleVO[]>('/roles')
  },
}
