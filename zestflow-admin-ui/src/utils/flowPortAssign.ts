/** 设计器连线端口分配 — 同节点入边上限 + 按方位分散端口，避免多线重合 */
import type { Edge, Graph, Node } from '@antv/x6'

export const MAX_LINKS_TO_NODE = 3

type Side = 'top' | 'bottom' | 'left' | 'right'
type Endpoint = 'source' | 'target'

const SIDE_PORTS: Record<Side, string[]> = {
  top: ['t', 'tl', 'tr'],
  bottom: ['b', 'bl', 'br'],
  left: ['l', 'tl', 'bl'],
  right: ['r', 'tr', 'br'],
}

function nodeCenter(node: Node) {
  const b = node.getBBox()
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 }
}

function listPortIds(node: Node): string[] {
  return node.getPorts().map(p => p.id).filter((id): id is string => !!id)
}

/** 从 from 指向 to 时，from 节点应使用的出口边 */
export function facingSide(from: Node, to: Node): Side {
  const a = nodeCenter(from)
  const b = nodeCenter(to)
  const dx = b.x - a.x
  const dy = b.y - a.y
  if (Math.abs(dy) >= Math.abs(dx)) {
    return dy > 0 ? 'bottom' : 'top'
  }
  return dx > 0 ? 'right' : 'left'
}

function sidePorts(side: Side, portIds: string[]): string[] {
  return SIDE_PORTS[side].filter(id => portIds.includes(id))
}

export function countIncomingEdges(graph: Graph, targetNodeId: string, excludeEdgeId?: string): number {
  return graph.getEdges().filter(e => {
    if (e.getTargetCellId() !== targetNodeId) return false
    if (excludeEdgeId && e.id === excludeEdgeId) return false
    return true
  }).length
}

export function canAddIncomingEdge(graph: Graph, targetNodeId: string, excludeEdgeId?: string): boolean {
  return countIncomingEdges(graph, targetNodeId, excludeEdgeId) < MAX_LINKS_TO_NODE
}

function otherNode(edge: Edge, end: Endpoint): Node | null {
  const cell = end === 'target' ? edge.getSourceCell() : edge.getTargetCell()
  return cell?.isNode() ? (cell as Node) : null
}

function sortEdgesByPeer(edges: Edge[], node: Node, end: Endpoint): Edge[] {
  return [...edges].sort((a, b) => {
    const pa = otherNode(a, end)
    const pb = otherNode(b, end)
    if (!pa || !pb) return 0
    const ca = nodeCenter(pa)
    const cb = nodeCenter(pb)
    const nc = nodeCenter(node)
    if (Math.abs(ca.y - nc.y) <= Math.abs(ca.x - nc.x)) {
      return ca.x - cb.x
    }
    return ca.y - cb.y
  })
}

function pickPortForIndex(side: Side, portIds: string[], index: number): string | null {
  const candidates = sidePorts(side, portIds)
  if (candidates.length === 0) return portIds[0] ?? null
  return candidates[Math.min(index, candidates.length - 1)]
}

function setEndpoint(edge: Edge, end: Endpoint, node: Node, portId: string) {
  if (end === 'source') {
    edge.setSource({ cell: node.id, port: portId })
  } else {
    edge.setTarget({ cell: node.id, port: portId })
  }
}

/** 按同侧分散端口，更新节点在某一端上的所有连线 */
export function rebalanceEndpointPorts(graph: Graph, node: Node, end: Endpoint): void {
  const edges = graph.getEdges().filter(e =>
    end === 'target' ? e.getTargetCellId() === node.id : e.getSourceCellId() === node.id,
  )
  if (edges.length === 0) return

  const portIds = listPortIds(node)
  if (portIds.length === 0) return

  const sorted = sortEdgesByPeer(edges, node, end)
  sorted.forEach((edge, index) => {
    const peer = otherNode(edge, end)
    if (!peer) return
    const side = facingSide(node, peer)
    const portId = pickPortForIndex(side, portIds, index)
    if (portId) setEndpoint(edge, end, node, portId)
  })
}

export function rebalanceEdgePortsForNode(graph: Graph, node: Node): void {
  rebalanceEndpointPorts(graph, node, 'target')
  rebalanceEndpointPorts(graph, node, 'source')
}

export function rebalanceAllEdgePorts(graph: Graph): void {
  graph.getNodes().forEach(n => rebalanceEdgePortsForNode(graph, n))
}

export function assignEdgeEndpoints(edge: Edge, graph: Graph): void {
  const source = edge.getSourceCell()
  const target = edge.getTargetCell()
  if (!source?.isNode() || !target?.isNode()) return
  rebalanceEdgePortsForNode(graph, target as Node)
  rebalanceEdgePortsForNode(graph, source as Node)
}
