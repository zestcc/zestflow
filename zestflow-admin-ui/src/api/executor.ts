import http from './index'

export interface ExecutorRegistryVO {
  id: number
  executorId: string
  appCode: string
  appName: string
  executorHost: string
  executorPort: number
  status: number
  lastHeartbeat: string | null
  updatedBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface AppOption {
  appCode: string
  appName: string
}

export const executorApi = {
  list() {
    return http.get<ExecutorRegistryVO[]>('/executors')
  },
  updateStatus(executorId: number, status: number) {
    return http.put<void>(`/executors/${executorId}/status`, { status })
  },
  listApps() {
    return http.get<AppOption[]>('/executors/apps')
  },
}
