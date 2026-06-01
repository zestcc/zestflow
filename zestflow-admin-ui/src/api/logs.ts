import request from './index'

export interface EventQueryParams {
  chainId?: string
  executionId?: string
  executorId?: string
  appName?: string
  appCode?: string
  eventTypes?: string[]
  startTime?: number
  endTime?: number
  status?: number
  keyword?: string
  page?: number
  pageSize?: number
}

export interface ExecutionTrace {
  executionId: string
  chainName: string
  executorId: string
  appCode: string
  appName: string
  startTime: number
  endTime: number
  costMs: number
  status: number
  eventCount: number
  nodeCount: number
  successCount: number
  failedCount: number
  errorMessage: string
  events: ChainEvent[]
}

export interface ChainEvent {
  eventId: string
  eventType: string
  executionId: string
  chainId: string
  chainName: string
  nodeId: string
  nodeName: string
  executorId: string
  appCode: string
  appName: string
  params: string
  result: string
  errorMessage: string
  costMs: number
  status: number
  timestamp: number
  metadata: string
}

export function queryEvents(params: EventQueryParams) {
  return request.post('/logs/events/query', params)
}

export function queryExecutionTraces(params: EventQueryParams) {
  return request.post('/logs/executions', params)
}

export function getExecutionTrace(executionId: string) {
  return request.get(`/logs/executions/${executionId}`)
}

/** 图数据快照 DTO */
export interface ChainSnapshotDTO {
  chainCode: string
  version: number
  graphData: string
  status: number
  appCode: string
  createdBy: string
  createdAt: string
}

/** 查询指定时刻的图数据快照 */
export function getSnapshot(chainCode: string, timestamp: number) {
  return request.get('/logs/snapshots', { params: { chainCode, timestamp } })
}
