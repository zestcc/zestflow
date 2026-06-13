import i18n from '@/i18n'
import type { ExecutionTrace } from './logs'

const baseURL = '/api/zestflow'

function buildAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
  }
  const token = localStorage.getItem('token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const locale = localStorage.getItem('locale') || 'zh-CN'
  headers['Accept-Language'] = locale
  const tenantId = localStorage.getItem('currentTenantId')
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId
  }
  return headers
}

export type ExecutionStreamEvent =
  | { type: 'connected' }
  | { type: 'waiting' }
  | { type: 'trace'; trace: ExecutionTrace }
  | { type: 'done' }
  | { type: 'error'; message: string }

export function isExecutionTerminal(trace: ExecutionTrace): boolean {
  if (trace.status === 0 || trace.status === 1) {
    return true
  }
  const terminal = new Set(['CHAIN_COMPLETED', 'CHAIN_FAILED', 'CHAIN_TIMEOUT'])
  return (trace.events || []).some(e => terminal.has(e.eventType))
}

function parseSseBlock(block: string, onEvent: (ev: ExecutionStreamEvent) => void) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (!dataLines.length) return
  const raw = dataLines.join('\n')
  try {
    const data = JSON.parse(raw)
    switch (eventName) {
      case 'connected':
        onEvent({ type: 'connected' })
        break
      case 'waiting':
        onEvent({ type: 'waiting' })
        break
      case 'trace':
        onEvent({ type: 'trace', trace: data as ExecutionTrace })
        break
      case 'done':
        onEvent({ type: 'done' })
        break
      case 'error':
        onEvent({ type: 'error', message: data.message ?? raw })
        break
      default:
        break
    }
  } catch {
    onEvent({ type: 'error', message: raw })
  }
}

export async function streamExecutionTrace(
  executionId: string,
  appCode: string | undefined,
  onEvent: (ev: ExecutionStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const query = appCode ? `?appCode=${encodeURIComponent(appCode)}` : ''
  const response = await fetch(`${baseURL}/logs/executions/${encodeURIComponent(executionId)}/stream${query}`, {
    method: 'GET',
    headers: buildAuthHeaders(),
    signal,
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || i18n.global.t('common.requestFailed'))
  }
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('stream unsupported')
  }
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      if (part.trim()) {
        parseSseBlock(part, onEvent)
      }
    }
  }
  if (buffer.trim()) {
    parseSseBlock(buffer, onEvent)
  }
}
