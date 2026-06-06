export interface ChainDiffSummary {
  nodesAdded: string[]
  nodesRemoved: string[]
  nodesChanged: string[]
  edgesAdded: number
  edgesRemoved: number
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

export function computeChainDiff(currentJson?: string | null, proposedJson?: string | null): ChainDiffSummary | null {
  if (!currentJson || !proposedJson) return null
  const current = parseChain(currentJson)
  const proposed = parseChain(proposedJson)

  const curMap = new Map(current.nodes.map(n => [String(n.id), n]))
  const propMap = new Map(proposed.nodes.map(n => [String(n.id), n]))

  const nodesAdded: string[] = []
  const nodesRemoved: string[] = []
  const nodesChanged: string[] = []

  for (const [id, n] of propMap) {
    const label = n.label || n.component || id
    if (!curMap.has(id)) {
      nodesAdded.push(String(label))
    } else if (nodeSignature(curMap.get(id)) !== nodeSignature(n)) {
      nodesChanged.push(String(label))
    }
  }
  for (const [id, n] of curMap) {
    if (!propMap.has(id)) {
      nodesRemoved.push(String(n.label || n.component || id))
    }
  }

  return {
    nodesAdded,
    nodesRemoved,
    nodesChanged,
    edgesAdded: Math.max(0, proposed.edges.length - current.edges.length),
    edgesRemoved: Math.max(0, current.edges.length - proposed.edges.length),
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
