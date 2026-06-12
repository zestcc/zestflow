import { describe, expect, it } from 'vitest'
import {
  MAX_LINKS_TO_NODE,
  facingSide,
  canAddIncomingEdge,
  countIncomingEdges,
  pickPortFacingPeer,
} from './flowPortAssign'

describe('flowPortAssign', () => {
  it('facingSide prefers vertical when dy dominates', () => {
    const from = { getBBox: () => ({ x: 100, y: 0, width: 100, height: 40 }) } as any
    const toBelow = { getBBox: () => ({ x: 100, y: 200, width: 100, height: 40 }) } as any
    const toAbove = { getBBox: () => ({ x: 100, y: -200, width: 100, height: 40 }) } as any
    expect(facingSide(from, toBelow)).toBe('bottom')
    expect(facingSide(from, toAbove)).toBe('top')
  })

  it('facingSide prefers horizontal when nodes are on the same row', () => {
    const from = { getBBox: () => ({ x: 0, y: 100, width: 100, height: 40 }) } as any
    const toRight = { getBBox: () => ({ x: 260, y: 108, width: 100, height: 40 }) } as any
    const toLeft = { getBBox: () => ({ x: -260, y: 92, width: 100, height: 40 }) } as any
    expect(facingSide(from, toRight)).toBe('right')
    expect(facingSide(from, toLeft)).toBe('left')
  })

  it('pickPortFacingPeer chooses left/right for diamond branch peers', () => {
    const cond = { getBBox: () => ({ x: 200, y: 100, width: 100, height: 80 }) } as any
    const leftPeer = { getBBox: () => ({ x: 40, y: 220, width: 120, height: 40 }) } as any
    const rightPeer = { getBBox: () => ({ x: 340, y: 220, width: 120, height: 40 }) } as any
    const ports = ['t', 'r', 'b', 'l']
    expect(pickPortFacingPeer(cond, leftPeer, ports)).toBe('l')
    expect(pickPortFacingPeer(cond, rightPeer, ports)).toBe('r')
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
