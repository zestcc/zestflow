import http from './index'

export interface ScheduleVO {
  id: number
  chainCode: string
  chainName: string
  cron: string
  routeStrategy: string
  params?: string
  status: number
  remark?: string
  createdBy?: string
  updatedBy?: string
  createdAt: string
  updatedAt: string
}

export interface ScheduleCreateDTO {
  chainCode: string
  chainName: string
  cron: string
  routeStrategy?: string
  params?: string
  remark?: string
}

export interface ScheduleUpdateDTO {
  cron?: string
  routeStrategy?: string
  params?: string
  remark?: string
  status?: number
}

export interface ScheduleLogVO {
  id: number
  scheduleId: number
  chainCode: string
  executorId?: string
  executorAddress?: string
  routeStrategy?: string
  triggerType: string
  params?: string
  status: number
  resultData?: string
  errorMessage?: string
  costMs?: number
  triggeredAt: string
  createdAt: string
}

export const scheduleApi = {
  list(params: { keyword?: string; status?: number; page?: number; size?: number }) {
    return http.get<{ records: ScheduleVO[]; total: number; current: number; size: number }>('/schedules', { params })
  },

  getById(id: number) {
    return http.get<ScheduleVO>(`/schedules/${id}`)
  },

  create(data: ScheduleCreateDTO) {
    return http.post<ScheduleVO>('/schedules', data)
  },

  update(id: number, data: ScheduleUpdateDTO) {
    return http.put<ScheduleVO>(`/schedules/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/schedules/${id}`)
  },

  toggleStatus(id: number) {
    return http.put<void>(`/schedules/${id}/status`)
  },

  trigger(id: number) {
    return http.post<ScheduleLogVO>(`/schedules/${id}/trigger`)
  },

  listLogs(params: { scheduleId?: number; status?: number; page?: number; size?: number }) {
    return http.get<{ records: ScheduleLogVO[]; total: number; current: number; size: number }>('/schedules/logs', { params })
  },
}
