import http from './index'

export interface DashboardStatsVO {
  totalModules: number
  totalExecutors: number
  healthyExecutors: number
  errorExecutors: number
  offlineExecutors: number
  totalChains: number
  enabledChains: number
  totalDesigns: number
  todayExecutions: number
  avgExecutionMs: number
  successRate: number
}

export const dashboardApi = {
  stats() {
    return http.get<DashboardStatsVO>('/dashboard/stats')
  },
}
