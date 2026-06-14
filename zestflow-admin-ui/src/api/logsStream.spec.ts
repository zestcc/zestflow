import { describe, expect, it } from 'vitest'

function dispatchStreamEvent(
  rawEvent: string,
  data: unknown,
  events: Array<{ type: string; trace?: unknown; message?: string }>,
) {
  switch (rawEvent) {
    case 'connected':
      events.push({ type: 'connected' })
      break
    case 'trace':
      events.push({ type: 'trace', trace: data })
      break
    case 'done':
      events.push({ type: 'done' })
      break
    case 'error':
      events.push({
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

describe('logsStream envelope', () => {
  it('parses websocket trace envelope', () => {
    const events: Array<{ type: string; trace?: unknown }> = []
    dispatchStreamEvent('trace', { executionId: 'ex-1', events: [] }, events)
    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('trace')
    expect(events[0].trace).toEqual({ executionId: 'ex-1', events: [] })
  })

  it('parses error envelope message field', () => {
    const events: Array<{ type: string; message?: string }> = []
    dispatchStreamEvent('error', { message: 'collector offline' }, events)
    expect(events[0]).toEqual({ type: 'error', message: 'collector offline' })
  })
})
