import i18n from '@/i18n'
import type { ExecutionTrace } from './logs'
import { isExecutionTerminal } from '@/utils/executionTrace'

export { isExecutionTerminal }

const baseURL = '/api/zestflow'

function buildAuthHeaders(extra: Record<string, string> = {}): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...extra,
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

let cachedWebsocketEnabled: boolean | null = null

export async function isLogStreamWebsocketEnabled(): Promise<boolean> {
  if (cachedWebsocketEnabled !== null) {
    return cachedWebsocketEnabled
  }
  try {
    const response = await fetch(`${baseURL}/system/features`, {
      headers: buildAuthHeaders(),
    })
    if (!response.ok) {
      cachedWebsocketEnabled = false
      return false
    }
    const json = (await response.json()) as { logLiveStream?: { websocketEnabled?: boolean } }
    cachedWebsocketEnabled = json.logLiveStream?.websocketEnabled === true
  } catch {
    cachedWebsocketEnabled = false
  }
  return cachedWebsocketEnabled
}

function dispatchStreamEvent(rawEvent: string, data: unknown, onEvent: (ev: ExecutionStreamEvent) => void) {
  switch (rawEvent) {
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
      onEvent({
        type: 'error',
        message: typeof data === 'object' && data && 'message' in data
          ? String((data as { message?: string }).message)
          : String(data),
      })
      break
    default:
      break
  }
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
    dispatchStreamEvent(eventName, JSON.parse(raw), onEvent)
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
    headers: buildAuthHeaders({ Accept: 'text/event-stream' }),
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

export function streamExecutionTraceWebSocket(
  executionId: string,
  appCode: string | undefined,
  onEvent: (ev: ExecutionStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const token = localStorage.getItem('token') ?? ''
    const params = new URLSearchParams()
    if (appCode) {
      params.set('appCode', appCode)
    }
    if (token) {
      params.set('access_token', token)
    }
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const qs = params.toString()
    const url = `${wsProtocol}//${window.location.host}${baseURL}/logs/executions/${encodeURIComponent(executionId)}/ws${qs ? `?${qs}` : ''}`
    const socket = new WebSocket(url)
    let settled = false

    const finish = (err?: Error) => {
      if (settled) return
      settled = true
      signal?.removeEventListener('abort', onAbort)
      if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
        socket.close()
      }
      if (err) {
        reject(err)
      } else {
        resolve()
      }
    }

    const onAbort = () => finish(new DOMException('Aborted', 'AbortError'))

    signal?.addEventListener('abort', onAbort)

    socket.onopen = () => {
      // 等待服务端推送 connected / trace
    }

    socket.onmessage = (message) => {
      try {
        const envelope = JSON.parse(String(message.data)) as { event?: string; data?: unknown }
        if (envelope.event) {
          dispatchStreamEvent(envelope.event, envelope.data, onEvent)
        }
      } catch {
        onEvent({ type: 'error', message: String(message.data) })
      }
    }

    socket.onerror = () => {
      finish(new Error(i18n.global.t('common.networkError')))
    }

    socket.onclose = () => {
      finish()
    }
  })
}

/** 优先 WebSocket（Admin features 开启时），失败回退 SSE。 */
export async function streamExecutionTraceAuto(
  executionId: string,
  appCode: string | undefined,
  onEvent: (ev: ExecutionStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  if (await isLogStreamWebsocketEnabled()) {
    try {
      await streamExecutionTraceWebSocket(executionId, appCode, onEvent, signal)
      return
    } catch (err) {
      if (signal?.aborted) {
        throw err
      }
    }
  }
  await streamExecutionTrace(executionId, appCode, onEvent, signal)
}
