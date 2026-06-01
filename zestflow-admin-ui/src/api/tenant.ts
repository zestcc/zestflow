import http from './index'

export interface TenantSimpleVO {
  id: number
  name: string
  code: string
  current?: boolean
  tenantAdmin?: boolean
}

export interface TenantVO {
  id: number
  name: string
  code: string
  description?: string
  status?: number
  lastActiveAt?: string
  createdBy?: string
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface TenantCreateDTO {
  name: string
  code: string
  description?: string
}

export interface TenantUpdateDTO {
  name?: string
  description?: string
  status?: number
}

export const tenantApi = {
  /** 获取当前用户的租户列表 */
  listMyTenants() {
    return http.get<TenantSimpleVO[]>('/auth/tenants')
  },

  /** 切换当前租户，返回新 JWT */
  switchTenant(id: number) {
    return http.post<{ token: string; user: any }>(`/auth/switch-tenant/${id}`)
  },

  /** 超管：获取所有租户列表 */
  listAll() {
    return http.get<TenantVO[]>('/tenants')
  },

  /** 超管：分页查询租户 */
  listPage(params: { name?: string; code?: string; page: number; size: number }) {
    return http.post<{ records: TenantVO[]; total: number }>('/tenants/page', params)
  },

  /** 超管：获取租户详情 */
  getById(id: number) {
    return http.get<TenantVO>(`/tenants/${id}`)
  },

  /** 超管：创建租户 */
  create(data: TenantCreateDTO) {
    return http.post<TenantVO>('/tenants', data)
  },

  /** 超管：更新租户 */
  update(id: number, data: TenantUpdateDTO) {
    return http.put<TenantVO>(`/tenants/${id}`, data)
  },

  /** 超管：删除租户 */
  delete(id: number) {
    return http.delete(`/tenants/${id}`)
  },
}
