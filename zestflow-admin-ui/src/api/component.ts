import http from './index'

export interface TagDef {
  name: string
  value: string
}

export interface ComponentVO {
  componentId: string
  componentName: string
  description?: string
  groupName: string
  timeout: number
  async: boolean
  componentType: string
  tagDefs?: TagDef[]
  status: number
  updatedAt: string
  executorSource?: string
  cachedAt?: string
}

export const componentApi = {
  list(params: {
    appCode?: string
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

  stats(params: { appCode?: string }) {
    return http.get<{ total: number; active: number; offline: number }>('/components/stats', { params })
  },
}
