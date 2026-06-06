import http from './index'

export interface AlertConfigVO {
  enabled: boolean
  cooldownMinutes: number
  windowMinutes: number
  minExecutions: number
  successRateThreshold: number
  failCountThreshold: number
  p95CostMsThreshold: number
  scheduleFailThreshold: number
  alertNoOnlineExecutor: boolean
  subjectPrefix: string
  scanIntervalMs: number
  defaults?: AlertConfigVO
  tenantOverride?: boolean
}

export interface AlertConfigSaveDTO {
  enabled?: boolean
  cooldownMinutes?: number
  windowMinutes?: number
  minExecutions?: number
  successRateThreshold?: number
  failCountThreshold?: number
  p95CostMsThreshold?: number
  scheduleFailThreshold?: number
  alertNoOnlineExecutor?: boolean
  subjectPrefix?: string
}

export interface AlertHistoryVO {
  id: number
  appCode: string
  ruleCode: string
  ruleLabel: string
  summary: string
  metrics?: Record<string, string>
  recipientCount?: number
  recipients?: string
  mailSent?: boolean
  sentAt?: string
}

export interface AlertScanResultVO {
  success: boolean
  summary?: string
  errorMessage?: string
  costMs?: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export const alertApi = {
  getConfig() {
    return http.get<AlertConfigVO>('/alerts/config')
  },

  saveConfig(data: AlertConfigSaveDTO) {
    return http.put<AlertConfigVO>('/alerts/config', data)
  },

  resetConfig() {
    return http.delete<AlertConfigVO>('/alerts/config')
  },

  scanNow() {
    return http.post<AlertScanResultVO>('/alerts/scan')
  },

  listHistory(params?: {
    appCode?: string
    ruleCode?: string
    startTime?: string
    endTime?: string
    page?: number
    size?: number
  }) {
    return http.get<PageResponse<AlertHistoryVO>>('/alerts/history', { params })
  },
}
