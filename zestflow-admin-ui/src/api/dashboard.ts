import http from './index'

export interface DashboardStatsVO {
  totalModules: number
  totalExecutors: number
  healthyExecutors: number
  errorExecutors: number
  offlineExecutors: number
}

export const dashboardApi = {
  stats() {
    return http.get<DashboardStatsVO>('/dashboard/stats')
  },
}
