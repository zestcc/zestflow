/** 设计器节点类型归一化（UPPERCASE，与 ChainConstants 对齐） */
import { NODE_TYPE_REGISTRY } from '@/config/nodeTypeRegistry'

export const FLOW_TERMINAL_TYPES = new Set(['start', 'end'])

/** 旧版小写 → 标准大写 */
const LEGACY_TYPE_MAP: Record<string, string> = {
  task: 'NORMAL',
  condition: 'CONDITION',
  multicondition: 'SELECTOR',
  loader: 'LOADER',
  parser: 'PARSER',
  script: 'SCRIPT',
  subchain: 'SUB_CHAIN',
  iterator: 'ITERATOR',
  transformer: 'TRANSFORMER',
  filter: 'FILTER',
  aggregator: 'AGGREGATOR',
  splitter: 'SPLITTER',
  httpclient: 'HTTP_CLIENT',
  cache: 'CACHE_READER',
  fork: 'FORK',
  join: 'JOIN',
  trycatch: 'TRY_CATCH',
  while: 'WHILE',
  logger: 'LOGGER',
  delay: 'DELAY',
  approval: 'APPROVAL',
  notification: 'NOTIFICATION',
}

const KNOWN_TYPES = new Set(NODE_TYPE_REGISTRY.map(m => m.type))

export function normalizeNodeType(nodeType?: string | null): string {
  if (!nodeType) return 'NORMAL'
  if (FLOW_TERMINAL_TYPES.has(nodeType)) return nodeType
  if (KNOWN_TYPES.has(nodeType)) return nodeType
  return LEGACY_TYPE_MAP[nodeType] || nodeType.toUpperCase()
}

/** 保存到 ChainDefinitionDTO 的 type 字段 */
export function mapNodeTypeToDto(nodeType: string): string {
  return normalizeNodeType(nodeType)
}

export const NODE_SHAPE_MAP: Record<string, string> = Object.fromEntries(
  NODE_TYPE_REGISTRY.map(m => [m.type, m.shape]),
)

export function getShapeForNodeType(nodeType: string): string {
  const t = normalizeNodeType(nodeType)
  return NODE_SHAPE_MAP[t] || 'flow-task'
}

export const NODE_SIZES: Record<string, [number, number]> = Object.fromEntries(
  NODE_TYPE_REGISTRY.map(m => [m.type, m.size]),
) as Record<string, [number, number]>

export function getNodeSize(nodeType: string): [number, number] {
  const t = normalizeNodeType(nodeType)
  return NODE_SIZES[t] || [160, 46]
}

const RECT_PORT_TYPES = new Set([
  ...NODE_TYPE_REGISTRY.filter(m => m.category !== 'terminal').map(m => m.type),
  ...FLOW_TERMINAL_TYPES,
])

export function isRectPortType(nodeType: string): boolean {
  return RECT_PORT_TYPES.has(normalizeNodeType(nodeType))
}

export const BINDABLE_NODE_TYPES = new Set(
  NODE_TYPE_REGISTRY.filter(m => m.bindable).map(m => m.type),
)

export function canBindNodeType(nodeType: string): boolean {
  return BINDABLE_NODE_TYPES.has(normalizeNodeType(nodeType))
}
