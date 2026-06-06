export interface ChainDiffSummary {
  nodesAdded: string[]
  nodesRemoved: string[]
  nodesChanged: string[]
  nodeIdsAdded: string[]
  nodeIdsRemoved: string[]
  nodeIdsChanged: string[]
  edgesAdded: number
  edgesRemoved: number
  edgeKeysAdded: string[]
  edgeKeysRemoved: string[]
}

function parseChain(json?: string | null): { nodes: any[]; edges: any[] } {
  if (!json) return { nodes: [], edges: [] }
  try {
    const root = typeof json === 'string' ? JSON.parse(json) : json
    return {
      nodes: Array.isArray(root?.nodes) ? root.nodes : [],
      edges: Array.isArray(root?.edges) ? root.edges : [],
    }
  } catch {
    return { nodes: [], edges: [] }
  }
}

function nodeSignature(n: any): string {
  if (!n) return ''
  return JSON.stringify({
    id: n.id,
    label: n.label,
    component: n.component ?? n.componentId,
    type: n.type ?? n.nodeType,
  })
}

function edgeKey(e: any): string {
  const source = e?.source ?? e?.from ?? ''
  const target = e?.target ?? e?.to ?? ''
  const label = e?.label ?? ''
  return `${source}->${target}:${label}`
}

export function computeChainDiff(currentJson?: string | null, proposedJson?: string | null): ChainDiffSummary | null {
  if (!currentJson || !proposedJson) return null
  const current = parseChain(currentJson)
  const proposed = parseChain(proposedJson)

  const curMap = new Map(current.nodes.map(n => [String(n.id), n]))
  const propMap = new Map(proposed.nodes.map(n => [String(n.id), n]))

  const nodesAdded: string[] = []
  const nodesRemoved: string[] = []
  const nodesChanged: string[] = []
  const nodeIdsAdded: string[] = []
  const nodeIdsRemoved: string[] = []
  const nodeIdsChanged: string[] = []

  for (const [id, n] of propMap) {
    const label = n.label || n.component || id
    if (!curMap.has(id)) {
      nodesAdded.push(String(label))
      nodeIdsAdded.push(String(id))
    } else if (nodeSignature(curMap.get(id)) !== nodeSignature(n)) {
      nodesChanged.push(String(label))
      nodeIdsChanged.push(String(id))
    }
  }
  for (const [id, n] of curMap) {
    if (!propMap.has(id)) {
      nodesRemoved.push(String(n.label || n.component || id))
      nodeIdsRemoved.push(String(id))
    }
  }

  const curEdgeKeys = new Set(current.edges.map(edgeKey))
  const propEdgeKeys = new Set(proposed.edges.map(edgeKey))
  const edgeKeysAdded = [...propEdgeKeys].filter(k => !curEdgeKeys.has(k))
  const edgeKeysRemoved = [...curEdgeKeys].filter(k => !propEdgeKeys.has(k))

  return {
    nodesAdded,
    nodesRemoved,
    nodesChanged,
    nodeIdsAdded,
    nodeIdsRemoved,
    nodeIdsChanged,
    edgesAdded: edgeKeysAdded.length,
    edgesRemoved: edgeKeysRemoved.length,
    edgeKeysAdded,
    edgeKeysRemoved,
  }
}

export function hasChainDiff(diff: ChainDiffSummary | null): boolean {
  if (!diff) return false
  return diff.nodesAdded.length > 0
    || diff.nodesRemoved.length > 0
    || diff.nodesChanged.length > 0
    || diff.edgesAdded > 0
    || diff.edgesRemoved > 0
}

export type AiDiffHighlightKind = 'added' | 'removed' | 'changed'

export function classifyNodeDiff(nodeId: string, diff: ChainDiffSummary | null): AiDiffHighlightKind | null {
  if (!diff || !nodeId) return null
  if (diff.nodeIdsAdded.includes(nodeId)) return 'added'
  if (diff.nodeIdsRemoved.includes(nodeId)) return 'removed'
  if (diff.nodeIdsChanged.includes(nodeId)) return 'changed'
  return null
}

export const AI_DIFF_STYLES: Record<AiDiffHighlightKind, { stroke: string; strokeWidth: number; strokeDasharray?: string }> = {
  added: { stroke: '#22c55e', strokeWidth: 3 },
  removed: { stroke: '#ef4444', strokeWidth: 3, strokeDasharray: '6,3' },
  changed: { stroke: '#f59e0b', strokeWidth: 3 },
}
