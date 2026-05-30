import http from './index'
import type { UserVO } from './auth'

export interface ModuleVO {
  id: number
  code: string
  name: string
  description?: string
  status: number
  owner?: string
  sortOrder: number
  executorTotal: number
  executorHealthy: number
  executorError: number
  executorOffline: number
  updatedBy?: string
}

export interface ModuleCreateDTO {
  code: string
  name: string
  description?: string
  status?: number
  owner?: string
  sortOrder?: number
}

export interface ModuleUpdateDTO {
  name?: string
  description?: string
  status?: number
  owner?: string
  sortOrder?: number
}

export const moduleApi = {
  list() {
    return http.get<ModuleVO[]>('/modules')
  },
  getById(id: number) {
    return http.get<ModuleVO>(`/modules/${id}`)
  },
  create(data: ModuleCreateDTO) {
    return http.post<ModuleVO>('/modules', data)
  },
  update(id: number, data: ModuleUpdateDTO) {
    return http.put<ModuleVO>(`/modules/${id}`, data)
  },
  delete(id: number) {
    return http.delete<void>(`/modules/${id}`)
  },
  listExecutors(id: number) {
    return http.get<ExecutorRegistryVO[]>(`/modules/${id}/executors`)
  },
  updateExecutorStatus(executorId: number, status: number) {
    return http.put<void>(`/modules/executors/${executorId}/status`, { status })
  },
}

export interface ExecutorRegistryVO {
  id: number
  moduleId: number
  moduleCode: string
  moduleName: string
  executorId: string
  executorHost: string
  executorPort: number
  status: number
  lastHeartbeat: string | null
  createdAt: string
  updatedBy?: string
}
