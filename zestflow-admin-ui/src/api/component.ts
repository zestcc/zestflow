import http from './index'

export interface ComponentVO {
  componentId: string
  componentName: string
  description?: string
  groupName: string
  timeout: number
  async: boolean
  componentType: string
  status: number
  updatedAt: string
  executorSource?: string
  cachedAt?: string
}

export const componentApi = {
  list(params: {
    moduleId?: number
    keyword?: string
    status?: number
    componentType?: string
    page?: number
    size?: number
  }) {
    return http.get<{
      records: ComponentVO[]
      total: number
      current: number
      size: number
    }>('/components', { params })
  },

  stats(params: { moduleId: number }) {
    return http.get<{ total: number; active: number; offline: number }>('/components/stats', { params })
  },
}
