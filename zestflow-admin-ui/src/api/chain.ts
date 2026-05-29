import http from './index'

export interface ChainVO {
  id: number
  code: string
  name: string
  moduleId: number
  status: number
  description?: string
  createdAt: string
  updatedAt: string
}

export interface ChainCreateDTO {
  name: string
  moduleId: number
  description?: string
  status?: number
}

export interface ChainUpdateDTO {
  name?: string
  description?: string
  status?: number
}

export const chainApi = {
  list(params: { moduleId: number; keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: ChainVO[]; total: number; current: number; size: number }>('/chains', { params })
  },

  getById(id: number) {
    return http.get<ChainVO>(`/chains/${id}`)
  },

  create(data: ChainCreateDTO) {
    return http.post<ChainVO>('/chains', data)
  },

  update(id: number, data: ChainUpdateDTO) {
    return http.put<ChainVO>(`/chains/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/chains/${id}`)
  },

  toggleStatus(id: number) {
    return http.put<void>(`/chains/${id}/status`)
  },
}
