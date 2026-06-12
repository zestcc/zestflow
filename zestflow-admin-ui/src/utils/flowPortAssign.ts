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

/** 端口在节点局部坐标系下的朝向角（弧度） */
const PORT_ANGLES: Record<string, number> = {
  t: -Math.PI / 2,
  tr: -Math.PI / 4,
  r: 0,
  br: Math.PI / 4,
  b: Math.PI / 2,
  bl: (3 * Math.PI) / 4,
  l: Math.PI,
  tl: (-3 * Math.PI) / 4,
}

function nodeCenter(node: Node) {
  const b = node.getBBox()
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 }
}

function listPortIds(node: Node): string[] {
  return node.getPorts().map(p => p.id).filter((id): id is string => !!id)
}

function angleDiff(a: number, b: number): number {
  let d = Math.abs(a - b)
  if (d > Math.PI) d = 2 * Math.PI - d
  return d
}

function peerAngle(from: Node, to: Node): number {
  const a = nodeCenter(from)
  const b = nodeCenter(to)
  return Math.atan2(b.y - a.y, b.x - a.x)
}

/** 按对端方位选择最贴合的端口（菱形/矩形通用，不依赖全局序号） */
export function pickPortFacingPeer(from: Node, to: Node, portIds: string[]): string | null {
  if (portIds.length === 0) return null
  const targetAngle = peerAngle(from, to)
  let best = portIds[0]
  let minDiff = Infinity
  for (const id of portIds) {
    const pa = PORT_ANGLES[id]
    if (pa === undefined) continue
    const diff = angleDiff(targetAngle, pa)
    if (diff < minDiff) {
      minDiff = diff
      best = id
    }
  }
  return best
}

/** 同行/同列对齐阈值（对标 draw.io 正交吸附） */
const ROW_ALIGN_THRESHOLD = 28
const COL_ALIGN_THRESHOLD = 28

/** 从 from 指向 to 时，from 节点应使用的出口边 */
export function facingSide(from: Node, to: Node): Side {
  const a = nodeCenter(from)
  const b = nodeCenter(to)
  const dx = b.x - a.x
  const dy = b.y - a.y
  const adx = Math.abs(dx)
  const ady = Math.abs(dy)
  if (ady <= ROW_ALIGN_THRESHOLD && adx > COL_ALIGN_THRESHOLD) {
    return dx > 0 ? 'right' : 'left'
  }
  if (adx <= COL_ALIGN_THRESHOLD && ady > ROW_ALIGN_THRESHOLD) {
    return dy > 0 ? 'bottom' : 'top'
  }
  if (ady >= adx) {
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
  const payload = { cell: node.id, port: portId }
  if (end === 'source') {
    edge.setSource(payload)
  } else {
    edge.setTarget(payload)
  }
}

function getEndpointPort(edge: Edge, end: Endpoint): string | undefined {
  const ep = end === 'source' ? edge.getSource() : edge.getTarget()
  if (!ep || !('cell' in ep)) return undefined
  return ep.port as string | undefined
}

/** 仅当新端口明显更贴合对端时才改，避免覆盖用户刚拖拽落点 */
function shouldReassignPort(from: Node, to: Node, currentPortId: string | undefined, portIds: string[]): boolean {
  const optimal = pickPortFacingPeer(from, to, portIds)
  if (!optimal) return false
  if (!currentPortId || !portIds.includes(currentPortId)) return true
  if (currentPortId === optimal) return false
  const angle = peerAngle(from, to)
  const curDiff = angleDiff(angle, PORT_ANGLES[currentPortId] ?? 0)
  const optDiff = angleDiff(angle, PORT_ANGLES[optimal] ?? 0)
  return optDiff < curDiff - 0.25
}

/**
 * 仅调整当前连线的源/目标端口（不重算同节点其它连线）。
 * 用户从端口拖出时保留落点；仅明显偏离时吸附到最近端口。
 */
export function snapEdgeEndpoints(edge: Edge, graph: Graph, force = false): void {
  const source = edge.getSourceCell()
  const target = edge.getTargetCell()
  if (!source?.isNode() || !target?.isNode()) return
  const srcNode = source as Node
  const tgtNode = target as Node
  const srcPorts = listPortIds(srcNode)
  const tgtPorts = listPortIds(tgtNode)

  if (srcPorts.length) {
    const cur = getEndpointPort(edge, 'source')
    if (force || shouldReassignPort(srcNode, tgtNode, cur, srcPorts)) {
      const port = pickPortFacingPeer(srcNode, tgtNode, srcPorts)
      if (port) setEndpoint(edge, 'source', srcNode, port)
    }
  }
  if (tgtPorts.length) {
    const cur = getEndpointPort(edge, 'target')
    if (force || shouldReassignPort(tgtNode, srcNode, cur, tgtPorts)) {
      const port = pickPortFacingPeer(tgtNode, srcNode, tgtPorts)
      if (port) setEndpoint(edge, 'target', tgtNode, port)
    }
  }
}

/** 按同侧分散端口，更新节点在某一端上的所有连线（加载历史图 / 多入边分散） */
export function rebalanceEndpointPorts(graph: Graph, node: Node, end: Endpoint): void {
  const edges = graph.getEdges().filter(e =>
    end === 'target' ? e.getTargetCellId() === node.id : e.getSourceCellId() === node.id,
  )
  if (edges.length === 0) return

  const portIds = listPortIds(node)
  if (portIds.length === 0) return

  const usedPorts = new Map<string, number>()

  const sorted = sortEdgesByPeer(edges, node, end)
  sorted.forEach((edge) => {
    const peer = otherNode(edge, end)
    if (!peer) return

    const anglePort = pickPortFacingPeer(node, peer, portIds)
    if (!anglePort) return

    const side = facingSide(node, peer)
    const sideCount = usedPorts.get(side) ?? 0
    const sideCandidate = pickPortForIndex(side, portIds, sideCount)
    const sidePortsOnShape = sidePorts(side, portIds)

    let portId = anglePort
    if (sidePortsOnShape.length > 1 && sideCount > 0) {
      portId = sideCandidate || anglePort
    }

    const portUsage = usedPorts.get(portId) ?? 0
    if (portUsage > 0 && sidePortsOnShape.length > 1) {
      portId = pickPortForIndex(side, portIds, sideCount) || anglePort
    }

    setEndpoint(edge, end, node, portId)
    usedPorts.set(side, sideCount + 1)
    usedPorts.set(portId, portUsage + 1)
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
  snapEdgeEndpoints(edge, graph, true)
}

/** 端口变更后刷新折线路由，避免连线视觉错位 */
export function refreshEdgeRouting(edge: Edge): void {
  const router = edge.getRouter()
  if (!router?.name) return
  edge.setRouter({ ...router, args: router.args ? { ...router.args } : undefined })
}

export function refreshNodeEdgeRouting(graph: Graph, node: Node): void {
  graph.getConnectedEdges(node).forEach(refreshEdgeRouting)
}

/** 新连线：只吸附当前边；目标节点多入边时再分散入边端口 */
export function onEdgeConnectedAssignPorts(graph: Graph, edge: Edge): void {
  snapEdgeEndpoints(edge, graph, false)
  const target = edge.getTargetCell()
  if (target?.isNode() && countIncomingEdges(graph, target.id) > 1) {
    rebalanceEndpointPorts(graph, target as Node, 'target')
  }
}
