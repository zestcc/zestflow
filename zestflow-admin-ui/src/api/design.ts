import http from './index'
import type { ChainVO } from './chain'
import type { AxiosRequestConfig } from 'axios'

export interface DesignVO {
  code: string
  name: string
  moduleId: number
  status: number
  description?: string
  graphData?: string
  designer?: string
  boundChainCodes?: string
  boundChains?: ChainVO[]
  chainCount?: number
  createdBy?: string
  updatedBy?: string
  createdAt: string
  updatedAt: string
}

export interface DesignCreateDTO {
  name: string
  moduleId: number
  description?: string
  designer?: string
}

export interface DesignUpdateDTO {
  name?: string
  description?: string
  designer?: string
  moduleId?: number
  status?: number
}

export const designApi = {
  list(params: { moduleId: number; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: DesignVO[]; total: number; current: number; size: number }>('/designs', { params })
  },

  getByCode(code: string, moduleId: number) {
    return http.get<DesignVO>(`/designs/${code}`, { params: { moduleId } })
  },

  create(data: DesignCreateDTO) {
    return http.post<DesignVO>('/designs', data)
  },

  update(code: string, data: DesignUpdateDTO) {
    return http.put<DesignVO>(`/designs/${code}`, data)
  },

  saveGraph(code: string, moduleId: number, graphData: string) {
    return http.put(`/designs/${code}/graph`, { graphData, moduleId })
  },

  delete(code: string, moduleId: number) {
    return http.delete(`/designs/${code}`, { params: { moduleId } } as AxiosRequestConfig)
  },

  toggleStatus(code: string, moduleId: number) {
    return http.put(`/designs/${code}/status?moduleId=${moduleId}`)
  },

  getBindings(code: string, moduleId: number) {
    return http.get<ChainVO[]>(`/designs/${code}/bindings`, { params: { moduleId } })
  },

  getBindable(code: string, moduleId: number) {
    return http.get<ChainVO[]>(`/designs/${code}/bindable`, { params: { moduleId } })
  },

  bind(designCode: string, chainCode: string, moduleId: number) {
    return http.post(`/designs/${designCode}/bindings?moduleId=${moduleId}`, { chainCode })
  },

  unbind(designCode: string, chainCode: string, moduleId: number) {
    return http.delete(`/designs/${designCode}/bindings/${chainCode}?moduleId=${moduleId}`)
  },
}
