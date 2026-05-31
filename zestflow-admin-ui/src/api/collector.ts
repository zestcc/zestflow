import http from './index'

export interface CollectorRegistryVO {
  id: number
  collectorId: string
  appCode: string
  appName: string
  collectorHost: string
  collectorPort: number
  status: number
  lastHeartbeat: string | null
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export const collectorApi = {
  list() {
    return http.get<CollectorRegistryVO[]>('/executors/collectors')
  },
  updateStatus(id: number, status: number) {
    return http.put<void>(`/executors/collectors/${id}/status`, { status })
  },
}
