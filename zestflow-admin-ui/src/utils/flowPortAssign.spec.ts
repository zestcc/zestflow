import { describe, expect, it } from 'vitest'
import {
  MAX_LINKS_TO_NODE,
  facingSide,
  canAddIncomingEdge,
  countIncomingEdges,
} from './flowPortAssign'

describe('flowPortAssign', () => {
  it('facingSide prefers vertical when dy dominates', () => {
    const from = { getBBox: () => ({ x: 100, y: 0, width: 100, height: 40 }) } as any
    const toBelow = { getBBox: () => ({ x: 100, y: 200, width: 100, height: 40 }) } as any
    const toAbove = { getBBox: () => ({ x: 100, y: -200, width: 100, height: 40 }) } as any
    expect(facingSide(from, toBelow)).toBe('bottom')
    expect(facingSide(from, toAbove)).toBe('top')
  })

  it('canAddIncomingEdge respects max limit', () => {
    const edges = [
      { id: 'e1', getTargetCellId: () => 't1' },
      { id: 'e2', getTargetCellId: () => 't1' },
      { id: 'e3', getTargetCellId: () => 't1' },
    ]
    const graph = { getEdges: () => edges } as any
    expect(countIncomingEdges(graph, 't1')).toBe(3)
    expect(canAddIncomingEdge(graph, 't1')).toBe(false)
    expect(canAddIncomingEdge(graph, 't1', 'e3')).toBe(true)
    expect(MAX_LINKS_TO_NODE).toBe(3)
  })
})
