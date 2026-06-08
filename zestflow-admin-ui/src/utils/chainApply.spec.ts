import { describe, expect, it } from 'vitest'
import {
  adjustConditionBranchLayout,
  collectBranchSubgraph,
  computeDagrePositions,
  type ChainEdgeDTO,
  type ChainNodeDTO,
} from './chainApply'

const nodeSize = (): [number, number] => [120, 48]

describe('chainApply condition layout', () => {
  it('collectBranchSubgraph isolates true branch', () => {
    const edges: ChainEdgeDTO[] = [
      { source: 'c', target: 't', label: 'True' },
      { source: 'c', target: 'f', label: 'False' },
      { source: 't', target: 'end' },
      { source: 'f', target: 'end' },
    ]
    const trueBranch = collectBranchSubgraph('c', 't', edges)
    expect(trueBranch.has('t')).toBe(true)
    expect(trueBranch.has('end')).toBe(true)
    expect(trueBranch.has('f')).toBe(false)
  })

  it('adjustConditionBranchLayout shifts true left and false right', () => {
    const nodes: ChainNodeDTO[] = [
      { id: 'c', type: 'CONDITION', label: '检查' },
      { id: 't', type: 'NORMAL', label: '成功' },
      { id: 'f', type: 'NORMAL', label: '失败' },
    ]
    const edges: ChainEdgeDTO[] = [
      { source: 'c', target: 't', label: 'True' },
      { source: 'c', target: 'f', label: 'False' },
    ]
    const base = new Map<string, { x: number; y: number }>([
      ['c', { x: 200, y: 0 }],
      ['t', { x: 200, y: 100 }],
      ['f', { x: 200, y: 100 }],
    ])
    const adjusted = adjustConditionBranchLayout(nodes, edges, base, 100)
    expect(adjusted.get('t')!.x).toBeLessThan(adjusted.get('f')!.x)
  })

  it('computeDagrePositions applies condition offset for diamond flow', () => {
    const nodes: ChainNodeDTO[] = [
      { id: 'start', type: 'START', label: '开始' },
      { id: 'cond', type: 'CONDITION', label: '条件' },
      { id: 'yes', type: 'NORMAL', label: '是' },
      { id: 'no', type: 'NORMAL', label: '否' },
      { id: 'end', type: 'END', label: '结束' },
    ]
    const edges: ChainEdgeDTO[] = [
      { source: 'start', target: 'cond' },
      { source: 'cond', target: 'yes', label: 'True' },
      { source: 'cond', target: 'no', label: 'False' },
      { source: 'yes', target: 'end' },
      { source: 'no', target: 'end' },
    ]
    const positions = computeDagrePositions(nodes, edges, nodeSize, { conditionBranchOffset: 80 })
    expect(positions.get('yes')!.x).toBeLessThan(positions.get('no')!.x)
    expect(positions.get('cond')!.y).toBeLessThan(positions.get('yes')!.y)
  })
})
