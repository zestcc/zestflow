import http from './index'
import type { AxiosRequestConfig } from 'axios'

export interface ChainVO {
  code: string
  name: string
  appCode?: string
  status: number
  description?: string
  designCode?: string
  createdBy?: string
  updatedBy?: string
  createdAt: string
  updatedAt: string
  publishedCount?: number
  totalExecutors?: number
}

export interface ChainCreateDTO {
  name: string
  appCode: string
  description?: string
}

export interface ChainUpdateDTO {
  name?: string
  description?: string
  appCode?: string
}

export const chainApi = {
  list(params: { appCode: string; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: ChainVO[]; total: number; current: number; size: number }>('/chains', { params })
  },

  getByCode(code: string, appCode: string) {
    return http.get<ChainVO>(`/chains/${code}`, { params: { appCode } })
  },

  create(data: ChainCreateDTO) {
    return http.post<ChainVO>('/chains', data)
  },

  update(code: string, data: ChainUpdateDTO) {
    return http.put<ChainVO>(`/chains/${code}`, data)
  },

  delete(code: string, appCode: string) {
    return http.delete(`/chains/${code}`, { params: { appCode } } as AxiosRequestConfig)
  },

  toggleStatus(code: string, appCode: string) {
    return http.put(`/chains/${code}/status?appCode=${appCode}`)
  },

  publish(code: string, appCode: string) {
    return http.post<{ code: number; message: string; total: number; success: number; details: Array<{ url: string; ok: boolean; message: string }> }>(`/chains/${code}/publish?appCode=${appCode}`)
  },
}
