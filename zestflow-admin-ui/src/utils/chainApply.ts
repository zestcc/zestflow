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
  return positions
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
    predicateMode: nt === 'CONDITION' ? 'script' : undefined,
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
