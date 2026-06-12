import dagre from '@dagrejs/dagre'
import { normalizeNodeType, getShapeForNodeType, getNodeSize } from '@/utils/nodeType'
import { defaultNodeFieldValues, getNodeTypeMeta, hydrateNodeDataFromConfig } from '@/config/nodeTypeRegistry'

export interface ChainNodeDTO {
  id: string
  label?: string
  type?: string
  component?: string
  componentName?: string
  script?: string
  subChainCode?: string
  groupName?: string
  description?: string
  config?: Record<string, any>
  paramResolvers?: Array<{ componentId: string; componentName?: string }>
  paramValidator?: { componentId: string; componentName?: string }
  preComponents?: Array<{ componentId: string; componentName?: string }>
  postComponents?: Array<{ componentId: string; componentName?: string }>
}

export interface ChainEdgeDTO {
  source: string
  target: string
  label?: string
}

export interface ChainDefinitionDTO {
  nodes?: ChainNodeDTO[]
  edges?: ChainEdgeDTO[]
}

export interface GraphApplyAdapter {
  getNodeById(id: string): { setData(data: Record<string, any>): void; getData(): Record<string, any> } | null
  addNode(options: {
    id: string
    shape: string
    x: number
    y: number
    width: number
    height: number
    data: Record<string, any>
  }): void
  addEdge(source: string, target: string, label?: string): void
  hasEdge(source: string, target: string, label?: string): boolean
  getLayoutMetrics(): { centerX: number; nextY: number; rowGap: number }
}

export interface DagreLayoutOptions {
  rankdir?: 'TB' | 'LR'
  nodesep?: number
  ranksep?: number
  /** CONDITION 分支左右间距（px），对标 BPMN True/False 分列 */
  conditionBranchOffset?: number
}

const TRUE_LABELS = /^(true|yes|是|成功|通过|y)$/i
const FALSE_LABELS = /^(false|no|否|失败|拒绝|n)$/i

function isConditionType(type?: string): boolean {
  const t = (type || 'NORMAL').toUpperCase()
  return t === 'CONDITION' || t === 'SELECTOR' || t === 'MULTICONDITION'
}

function classifyBranchLabel(label?: string): 'true' | 'false' | 'other' {
  const l = (label || '').trim()
  if (!l) return 'other'
  if (TRUE_LABELS.test(l)) return 'true'
  if (FALSE_LABELS.test(l)) return 'false'
  return 'other'
}

/** 从 condition 节点沿单条出边收集可达子图（不穿越兄弟分支起点） */
export function collectBranchSubgraph(
  conditionId: string,
  branchTargetId: string,
  edges: ChainEdgeDTO[],
): Set<string> {
  const siblingTargets = new Set(
    edges.filter((e) => e.source === conditionId).map((e) => String(e.target)),
  )
  siblingTargets.delete(branchTargetId)

  const result = new Set<string>()
  const queue = [branchTargetId]
  while (queue.length > 0) {
    const id = queue.shift()!
    if (result.has(id)) continue
    result.add(id)
    for (const e of edges) {
      if (e.source !== id) continue
      const target = String(e.target)
      if (siblingTargets.has(target) && target !== branchTargetId) continue
      queue.push(target)
    }
  }
  return result
}

/**
 * CONDITION 多分支布局后处理：True 左 / False 右（对标 Camunda/BPMN 读法）。
 * 在 dagre TB 布局之后调用。
 */
export function adjustConditionBranchLayout(
  nodes: ChainNodeDTO[],
  edges: ChainEdgeDTO[],
  positions: Map<string, { x: number; y: number }>,
  branchOffset = 120,
): Map<string, { x: number; y: number }> {
  const adjusted = new Map(positions)

  for (const node of nodes) {
    if (!node?.id || !isConditionType(node.type)) continue
    const conditionId = String(node.id)
    const condPos = adjusted.get(conditionId)
    if (!condPos) continue

    const outgoing = edges.filter((e) => e?.source === conditionId)
    if (outgoing.length < 2) continue

    for (const edge of outgoing) {
      const kind = classifyBranchLabel(edge.label)
      if (kind === 'other') continue
      const targetId = String(edge.target)
      const pos = adjusted.get(targetId)
      if (!pos) continue
      const targetCenterX = kind === 'true' ? condPos.x - branchOffset : condPos.x + branchOffset
      const delta = targetCenterX - pos.x
      if (delta === 0) continue
      const subgraph = collectBranchSubgraph(conditionId, targetId, edges)
      subgraph.forEach((nodeId) => {
        const p = adjusted.get(nodeId)
        if (p) adjusted.set(nodeId, { x: p.x + delta, y: p.y })
      })
    }
  }

  return adjusted
}

/** 基于 dagre 计算节点左上角坐标（相对布局，不含画布偏移） */
export function computeDagrePositions(
  nodes: ChainNodeDTO[],
  edges: ChainEdgeDTO[],
  nodeSize: (node: ChainNodeDTO) => [number, number],
  options: DagreLayoutOptions = {},
): Map<string, { x: number; y: number }> {
  const g = new dagre.graphlib.Graph()
  g.setGraph({
    rankdir: options.rankdir ?? 'TB',
    nodesep: options.nodesep ?? 48,
    ranksep: options.ranksep ?? 72,
    marginx: 0,
    marginy: 0,
  })
  g.setDefaultEdgeLabel(() => ({}))

  nodes.forEach((chainNode) => {
    if (!chainNode?.id) return
    const id = String(chainNode.id)
    const [w, h] = nodeSize(chainNode)
    g.setNode(id, { width: w, height: h })
  })

  edges.forEach((edge) => {
    if (!edge?.source || !edge?.target) return
    const source = String(edge.source)
    const target = String(edge.target)
    if (!g.hasNode(source) || !g.hasNode(target)) return
    g.setEdge(source, target)
  })

  dagre.layout(g)

  const positions = new Map<string, { x: number; y: number }>()
  nodes.forEach((chainNode) => {
    if (!chainNode?.id) return
    const id = String(chainNode.id)
    const laid = g.node(id)
    if (!laid) return
    positions.set(id, { x: laid.x - laid.width / 2, y: laid.y - laid.height / 2 })
  })

  return adjustConditionBranchLayout(
    nodes,
    edges,
    positions,
    options.conditionBranchOffset ?? 120,
  )
}

function anchorDagrePositions(
  positions: Map<string, { x: number; y: number }>,
  centerX: number,
  startY: number,
): Map<string, { x: number; y: number }> {
  if (positions.size === 0) return positions
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  positions.forEach((p) => {
    minX = Math.min(minX, p.x)
    minY = Math.min(minY, p.y)
    maxX = Math.max(maxX, p.x)
  })
  const graphCenter = minX + (maxX - minX) / 2
  const offsetX = centerX - graphCenter
  const offsetY = startY - minY
  const anchored = new Map<string, { x: number; y: number }>()
  positions.forEach((p, id) => {
    anchored.set(id, { x: p.x + offsetX, y: p.y + offsetY })
  })
  return anchored
}

function buildBaseNodeData(type: string, label: string, inlinePredId: () => string): Record<string, any> {
  const nt = normalizeNodeType(type)
  const meta = getNodeTypeMeta(nt)
  const fieldDefaults = meta ? defaultNodeFieldValues(meta) : {}
  return {
    label,
    nodeType: nt,
    description: '',
    preComponents: [],
    postComponents: [],
    paramResolvers: [],
    paramValidatorId: '',
    paramValidatorName: '',
    executeStrategy: 'NORMAL',
    transactionPropagation: 'INHERIT',
    script: '',
    subChainCode: '',
    iteratorDataSource: '',
    iteratorItemName: 'item',
    predicateMode: nt === 'CONDITION' ? 'bind' : undefined,
    predicateScript: nt === 'CONDITION' ? '' : undefined,
    trueLabel: nt === 'CONDITION' ? 'True' : undefined,
    falseLabel: nt === 'CONDITION' ? 'False' : undefined,
    componentId: nt === 'CONDITION' ? inlinePredId() : '',
    ...fieldDefaults,
  }
}

export function chainNodeToGraphData(chainNode: ChainNodeDTO, inlinePredId: () => string): Record<string, any> {
  const nodeType = normalizeNodeType(chainNode.type || 'NORMAL')
  const data = buildBaseNodeData(nodeType, chainNode.label || chainNode.id, inlinePredId)
  if (chainNode.component) data.componentId = chainNode.component
  if (chainNode.componentName) data.componentName = chainNode.componentName
  if (chainNode.script) data.script = chainNode.script
  if (chainNode.subChainCode) data.subChainCode = chainNode.subChainCode
  if (chainNode.groupName) data.groupName = chainNode.groupName
  if (chainNode.description) data.description = chainNode.description
  if (chainNode.config) {
    hydrateNodeDataFromConfig(nodeType, data, chainNode.config)
    if (nodeType === 'CONDITION') {
      if (chainNode.config.predicateMode) data.predicateMode = chainNode.config.predicateMode
      if (chainNode.config.predicateScript) data.predicateScript = chainNode.config.predicateScript
      if (chainNode.config.trueLabel) data.trueLabel = chainNode.config.trueLabel
      if (chainNode.config.falseLabel) data.falseLabel = chainNode.config.falseLabel
    }
  }
  if (chainNode.paramResolvers?.length) {
    data.paramResolvers = chainNode.paramResolvers.map(p => ({
      componentId: p.componentId,
      componentName: p.componentName || '',
    }))
  }
  if (chainNode.paramValidator?.componentId) {
    data.paramValidatorId = chainNode.paramValidator.componentId
    data.paramValidatorName = chainNode.paramValidator.componentName || ''
  }
  if (chainNode.preComponents?.length) {
    data.preComponents = chainNode.preComponents.map(p => ({
      componentId: p.componentId,
      componentName: p.componentName || '',
    }))
  }
  if (chainNode.postComponents?.length) {
    data.postComponents = chainNode.postComponents.map(p => ({
      componentId: p.componentId,
      componentName: p.componentName || '',
    }))
  }
  return data
}

export function applyChainDefinitionToGraph(
  chain: ChainDefinitionDTO,
  adapter: GraphApplyAdapter,
  inlinePredId: () => string,
): void {
  const nodes = Array.isArray(chain.nodes) ? chain.nodes : []
  const edges = Array.isArray(chain.edges) ? chain.edges : []
  const metrics = adapter.getLayoutMetrics()

  const nodeSize = (chainNode: ChainNodeDTO): [number, number] => {
    const nodeType = normalizeNodeType(chainNode.type || 'NORMAL')
    return getNodeSize(nodeType)
  }

  const rawPositions = computeDagrePositions(nodes, edges, nodeSize)
  const positions = anchorDagrePositions(rawPositions, metrics.centerX, metrics.nextY)

  nodes.forEach((chainNode) => {
    if (!chainNode?.id) return
    const existing = adapter.getNodeById(String(chainNode.id))
    const graphData = chainNodeToGraphData(chainNode, inlinePredId)
    if (existing) {
      const merged = { ...(existing.getData() || {}), ...graphData }
      existing.setData(merged)
      return
    }
    const nodeType = normalizeNodeType(chainNode.type || 'NORMAL')
    const [w, h] = nodeSize(chainNode)
    const pos = positions.get(String(chainNode.id))
    const x = pos?.x ?? metrics.centerX - w / 2
    const y = pos?.y ?? metrics.nextY
    adapter.addNode({
      id: String(chainNode.id),
      shape: getShapeForNodeType(nodeType),
      x,
      y,
      width: w,
      height: h,
      data: graphData,
    })
  })

  edges.forEach((edge) => {
    if (!edge?.source || !edge?.target) return
    if (adapter.hasEdge(String(edge.source), String(edge.target), edge.label)) return
    adapter.addEdge(String(edge.source), String(edge.target), edge.label)
  })
}
