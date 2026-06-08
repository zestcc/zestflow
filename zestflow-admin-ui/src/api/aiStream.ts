import axios from 'axios'
import i18n from '@/i18n'
import type {
  AiExplainRequest,
  AiExplainResponse,
  AiSuggestRequest,
  AiSuggestResponse,
} from './ai'

const baseURL = '/api/zestflow'

export type CopilotStreamEvent =
  | { type: 'progress'; step: string }
  | { type: 'reasoning'; delta: string }
  | { type: 'content'; delta: string }
  | { type: 'done'; payload: AiSuggestResponse | AiExplainResponse }
  | { type: 'error'; message: string }

function buildAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
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

function parseSseBlock(block: string, onEvent: (ev: CopilotStreamEvent) => void) {
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
      case 'progress':
        onEvent({ type: 'progress', step: data.step ?? raw })
        break
      case 'reasoning':
        onEvent({ type: 'reasoning', delta: data.delta ?? '' })
        break
      case 'content':
        onEvent({ type: 'content', delta: data.delta ?? '' })
        break
      case 'done':
        onEvent({ type: 'done', payload: data })
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

async function postSse(
  path: string,
  body: unknown,
  onEvent: (ev: CopilotStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${baseURL}${path}`, {
    method: 'POST',
    headers: buildAuthHeaders(),
    body: JSON.stringify(body),
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

export function streamSuggest(
  data: AiSuggestRequest,
  onEvent: (ev: CopilotStreamEvent) => void,
  signal?: AbortSignal,
) {
  return postSse('/ai/stream/design/suggest', data, onEvent, signal)
}

export function streamExplain(
  data: AiExplainRequest,
  onEvent: (ev: CopilotStreamEvent) => void,
  signal?: AbortSignal,
) {
  return postSse('/ai/stream/design/explain', data, onEvent, signal)
}

/** 保留 axios 实例供非流式降级 */
export const aiStreamClient = axios.create({ baseURL, timeout: 180_000 })
