import request from './index'

export interface EventQueryParams {
  chainId?: string
  executorId?: string
  appName?: string
  eventTypes?: string[]
  startTime?: number
  endTime?: number
  status?: number
  keyword?: string
  page?: number
  pageSize?: number
}

export function queryEvents(params: EventQueryParams) {
  return request.post('/api/logs/events/query', params)
}
