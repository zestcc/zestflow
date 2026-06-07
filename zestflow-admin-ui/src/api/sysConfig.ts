import http from './index'

export interface SysConfigVO {
  id: number
  configKey: string
  configName: string
  configValue: string | null
  valueType: string
  category: string
  status: number
  sort: number
  remark: string | null
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface SysConfigCreateDTO {
  configKey: string
  configName: string
  configValue?: string
  valueType?: string
  category?: string
  status?: number
  sort?: number
  remark?: string
}

export interface SysConfigUpdateDTO {
  configName?: string
  configValue?: string
  valueType?: string
  category?: string
  status?: number
  sort?: number
  remark?: string
}

export interface PageRes<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export const sysConfigApi = {
  list(params?: { keyword?: string; category?: string; status?: number; page?: number; size?: number }) {
    return http.get<PageRes<SysConfigVO>>('/sys-configs', { params })
  },
  categories() {
    return http.get<string[]>('/sys-configs/categories')
  },
  getById(id: number) {
    return http.get<SysConfigVO>(`/sys-configs/${id}`)
  },
  create(dto: SysConfigCreateDTO) {
    return http.post<SysConfigVO>('/sys-configs', dto)
  },
  update(id: number, dto: SysConfigUpdateDTO) {
    return http.put<SysConfigVO>(`/sys-configs/${id}`, dto)
  },
  delete(id: number) {
    return http.delete<void>(`/sys-configs/${id}`)
  },
  toggleStatus(id: number) {
    return http.put<void>(`/sys-configs/${id}/status`, {})
  },
}
