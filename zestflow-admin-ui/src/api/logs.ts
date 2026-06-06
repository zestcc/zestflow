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
  chainCode: string
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

export interface NodeExecutionDetail {
  executionId: string
  nodeId: string
  nodeName: string
  nodeShape: string
  params: string
  result: string
  errorMessage: string
  costMs: number
  status: number
  timeline: ChainEvent[]
}

export function getNodeExecutionDetail(
  executionId: string,
  nodeId: string,
  nodeShape?: string,
  appCode?: string,
) {
  return request.get(`/logs/executions/${executionId}/nodes/${encodeURIComponent(nodeId)}`, {
    params: { nodeShape, appCode },
  })
}

export interface EventStats {
  totalCount: number
  executionCount: number
  successCount: number
  inProgressCount: number
  successRate: number
  avgCostMs: number
  p95CostMs: number
  maxCostMs: number
  failCount: number
  typeDistribution?: Record<string, number>
}

export interface LogAnalyticsParams {
  tenantId?: number
  appCode?: string
  executorId?: string
  chainId?: string
  startTime?: number
  endTime?: number
  granularity?: 'hour' | 'day'
  limit?: number
  rankBy?: 'count' | 'fail' | 'slow'
}

export interface ExecutionTrendPoint {
  bucketStart: number
  totalCount: number
  successCount: number
  failCount: number
  avgCostMs: number
}

export interface ExecutionRankItem {
  key: string
  name: string
  totalCount: number
  failCount: number
  successRate: number
  avgCostMs: number
  maxCostMs: number
}

export interface FailureClusterItem {
  errorSummary: string
  count: number
  lastSeen: number
}

export function queryLogStats(params: LogAnalyticsParams) {
  return request.post<EventStats>('/logs/analytics/stats', params)
}

export function queryLogTrend(params: LogAnalyticsParams) {
  return request.post<ExecutionTrendPoint[]>('/logs/analytics/trend', params)
}

export function queryChainRanking(params: LogAnalyticsParams) {
  return request.post<ExecutionRankItem[]>('/logs/analytics/rankings/chains', params)
}

export function queryExecutorRanking(params: LogAnalyticsParams) {
  return request.post<ExecutionRankItem[]>('/logs/analytics/rankings/executors', params)
}

export function queryNodeRanking(params: LogAnalyticsParams) {
  return request.post<ExecutionRankItem[]>('/logs/analytics/rankings/nodes', params)
}

export function queryFailureClusters(params: LogAnalyticsParams) {
  return request.post<FailureClusterItem[]>('/logs/analytics/failures/clusters', params)
}
