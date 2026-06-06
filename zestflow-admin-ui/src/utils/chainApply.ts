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
  let row = 0

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
    const [w, h] = getNodeSize(nodeType)
    const x = metrics.centerX - w / 2
    const y = metrics.nextY + row * metrics.rowGap
    row += 1
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
