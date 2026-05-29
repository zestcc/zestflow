import http from './index'
import type { ChainVO } from './chain'

export interface DesignVO {
  id: number
  code: string
  name: string
  moduleId: number
  status: number
  description?: string
  graphData?: string
  designer?: string
  boundChainCodes?: string
  boundChains?: ChainVO[]
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
  code?: string
  name?: string
  description?: string
  status?: number
  graphData?: string
  designer?: string
}

export const designApi = {
  list(params: { moduleId: number; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: DesignVO[]; total: number; current: number; size: number }>('/designs', { params })
  },

  getById(id: number) {
    return http.get<DesignVO>(`/designs/${id}`)
  },

  create(data: DesignCreateDTO) {
    return http.post<DesignVO>('/designs', data)
  },

  update(id: number, data: DesignUpdateDTO) {
    return http.put<DesignVO>(`/designs/${id}`, data)
  },

  saveGraph(id: number, graphData: string) {
    return http.put<void>(`/designs/${id}/graph`, { graphData })
  },

  delete(id: number) {
    return http.delete<void>(`/designs/${id}`)
  },

  toggleStatus(id: number) {
    return http.put<void>(`/designs/${id}/status`)
  },

  getBindings(id: number) {
    return http.get<ChainVO[]>(`/designs/${id}/bindings`)
  },

  getBindable(id: number) {
    return http.get<ChainVO[]>(`/designs/${id}/bindable`)
  },

  bind(id: number, chainId: number) {
    return http.post<void>(`/designs/${id}/bindings`, { chainId })
  },

  unbind(id: number, chainId: number) {
    return http.delete<void>(`/designs/${id}/bindings/${chainId}`)
  },
}
