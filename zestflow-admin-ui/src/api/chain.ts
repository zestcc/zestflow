import http from './index'
import type { AxiosRequestConfig } from 'axios'

export interface ChainVO {
  code: string
  name: string
  moduleId: number
  status: number
  description?: string
  designCode?: string
  createdBy?: string
  updatedBy?: string
  createdAt: string
  updatedAt: string
}

export interface ChainCreateDTO {
  name: string
  moduleId: number
  description?: string
}

export interface ChainUpdateDTO {
  name?: string
  description?: string
  moduleId?: number
}

export const chainApi = {
  list(params: { moduleId: number; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: ChainVO[]; total: number; current: number; size: number }>('/chains', { params })
  },

  getByCode(code: string, moduleId: number) {
    return http.get<ChainVO>(`/chains/${code}`, { params: { moduleId } })
  },

  create(data: ChainCreateDTO) {
    return http.post<ChainVO>('/chains', data)
  },

  update(code: string, data: ChainUpdateDTO) {
    return http.put<ChainVO>(`/chains/${code}`, data)
  },

  delete(code: string, moduleId: number) {
    return http.delete(`/chains/${code}`, { params: { moduleId } } as AxiosRequestConfig)
  },

  toggleStatus(code: string, moduleId: number) {
    return http.put(`/chains/${code}/status?moduleId=${moduleId}`)
  },

  publish(code: string, moduleId: number) {
    return http.post<{ code: number; message: string; total: number; success: number; details: Array<{ url: string; ok: boolean; message: string }> }>(`/chains/${code}/publish?moduleId=${moduleId}`)
  },
}
