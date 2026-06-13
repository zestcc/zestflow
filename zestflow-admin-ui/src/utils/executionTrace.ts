import type { ExecutionTrace } from '@/api/logs'

/** 与后端 {@code com.zestflow.common.protocol.ExecutionTraceSupport} 保持一致 */
const TERMINAL_EVENT_TYPES = new Set(['CHAIN_COMPLETED', 'CHAIN_FAILED', 'CHAIN_TIMEOUT'])

export function isExecutionTerminal(trace: ExecutionTrace): boolean {
  if (trace.status === 0 || trace.status === 1) {
    return true
  }
  return (trace.events || []).some(e => e.eventType != null && TERMINAL_EVENT_TYPES.has(String(e.eventType)))
}

export function executionTraceFingerprint(trace: ExecutionTrace): number {
  const events = trace.events?.length ?? trace.eventCount ?? 0
  return hashCombine(
    events,
    trace.status ?? -1,
    trace.endTime ?? 0,
    trace.failedCount ?? 0,
    trace.successCount ?? 0,
  )
}

function hashCombine(...values: number[]): number {
  let h = 0
  for (const v of values) {
    h = Math.imul(31, h) + v
    h |= 0
  }
  return h
}
