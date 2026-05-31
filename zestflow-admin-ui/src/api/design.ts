import http from './index'
import type { ChainVO } from './chain'
import type { AxiosRequestConfig } from 'axios'

export interface DesignVO {
  code: string
  name: string
  status: number
  description?: string
  graphData?: string
  chainData?: string
  designer?: string
  boundChainCodes?: string
  boundChains?: ChainVO[]
  chainCount?: number
  createdBy?: string
  updatedBy?: string
  createdAt: string
  updatedAt: string
  appCode?: string
}

export interface DesignCreateDTO {
  name: string
  appCode: string
  description?: string
  designer?: string
}

export interface DesignUpdateDTO {
  name?: string
  description?: string
  designer?: string
  appCode?: string
}

export const designApi = {
  list(params: { appCode?: string; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: DesignVO[]; total: number; current: number; size: number }>('/designs', { params })
  },

  getByCode(code: string, appCode: string) {
    return http.get<DesignVO>(`/designs/${code}`, { params: { appCode } })
  },

  create(data: DesignCreateDTO) {
    return http.post<DesignVO>('/designs', data)
  },

  update(code: string, data: DesignUpdateDTO) {
    return http.put<DesignVO>(`/designs/${code}`, data)
  },

  saveGraph(code: string, appCode: string, graphData: string, chainData?: string) {
    return http.put(`/designs/${code}/graph`, { graphData, chainData, appCode })
  },

  delete(code: string, appCode: string) {
    return http.delete(`/designs/${code}`, { params: { appCode } } as AxiosRequestConfig)
  },

  toggleStatus(code: string, appCode: string) {
    return http.put(`/designs/${code}/status?appCode=${appCode}`)
  },

  getBindings(code: string, appCode: string) {
    return http.get<ChainVO[]>(`/designs/${code}/bindings`, { params: { appCode } })
  },

  getBindable(code: string, appCode: string) {
    return http.get<ChainVO[]>(`/designs/${code}/bindable`, { params: { appCode } })
  },

  bind(designCode: string, chainCode: string, appCode: string) {
    return http.post(`/designs/${designCode}/bindings?appCode=${appCode}`, { chainCode })
  },

  unbind(designCode: string, chainCode: string, appCode: string) {
    return http.delete(`/designs/${designCode}/bindings/${chainCode}?appCode=${appCode}`)
  },
}
