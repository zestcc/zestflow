/**
 * 节点类型注册表 — 对齐 ChainConstants + 市面 BPM（Camunda/Activiti/n8n）常见节点语义。
 * 设计器 palette / shape / 属性面板 / 链 DTO config 序列化均由此驱动。
 */
import { normalizeNodeType } from '@/utils/nodeType'

export type NodeCategory = 'terminal' | 'flow' | 'data' | 'integration' | 'human' | 'aux' | 'component'

export interface NodeTypeFieldMeta {
  key: string
  /** i18n key under design.nodeFields.* */
  i18nKey: string
  input: 'text' | 'textarea' | 'number' | 'select'
  placeholder?: string
  options?: { value: string; labelKey: string }[]
  defaultValue?: string | number
  /** persist to ChainNodeDTO.config */
  inConfig?: boolean
}

export interface NodeTypeMeta {
  type: string
  category: NodeCategory
  color: string
  shape: string
  size: [number, number]
  /** design.xxxNode i18n key suffix */
  i18nKey: string
  bindable: boolean
  hasDescription: boolean
  hasScript: boolean
  icon: string
  fields: NodeTypeFieldMeta[]
}

const ICON_TASK = '<svg viewBox="0 0 14 14"><rect x="2" y="1" width="10" height="12" rx="2" fill="currentColor"/></svg>'
const ICON_DIAMOND = '<svg viewBox="0 0 14 14"><polygon points="7,0 14,7 7,14 0,7" fill="currentColor"/></svg>'

export const NODE_TYPE_REGISTRY: NodeTypeMeta[] = [
  { type: 'start', category: 'terminal', color: '#22c55e', shape: 'flow-start', size: [148, 40], i18nKey: 'startNode', bindable: false, hasDescription: false, hasScript: false, icon: '<svg viewBox="0 0 14 14"><circle cx="7" cy="7" r="6" fill="currentColor"/></svg>', fields: [] },
  { type: 'end', category: 'terminal', color: '#6b7280', shape: 'flow-end', size: [148, 40], i18nKey: 'endNode', bindable: false, hasDescription: false, hasScript: false, icon: '<svg viewBox="0 0 14 14"><circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="7" cy="7" r="2" fill="currentColor"/></svg>', fields: [] },
  { type: 'NORMAL', category: 'flow', color: '#3b82f6', shape: 'flow-task', size: [160, 46], i18nKey: 'taskNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [] },
  { type: 'LOADER', category: 'component', color: '#06b6d4', shape: 'flow-loader', size: [160, 46], i18nKey: 'loaderNode', bindable: true, hasDescription: true, hasScript: true, icon: ICON_TASK, fields: [] },
  { type: 'PARSER', category: 'component', color: '#ec4899', shape: 'flow-parser', size: [160, 46], i18nKey: 'parserNode', bindable: true, hasDescription: true, hasScript: true, icon: ICON_TASK, fields: [] },
  { type: 'CONDITION', category: 'flow', color: '#f59e0b', shape: 'flow-condition', size: [100, 80], i18nKey: 'conditionNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_DIAMOND, fields: [] },
  { type: 'SELECTOR', category: 'flow', color: '#8b5cf6', shape: 'flow-multicondition', size: [120, 80], i18nKey: 'multiconditionNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_DIAMOND, fields: [] },
  { type: 'SCRIPT', category: 'flow', color: '#8b5cf6', shape: 'flow-script', size: [160, 46], i18nKey: 'scriptNode', bindable: false, hasDescription: true, hasScript: true, icon: ICON_TASK, fields: [] },
  { type: 'SUB_CHAIN', category: 'flow', color: '#06b6d4', shape: 'flow-subchain', size: [160, 46], i18nKey: 'subchainNode', bindable: false, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [] },
  { type: 'ITERATOR', category: 'flow', color: '#f97316', shape: 'flow-iterator', size: [160, 46], i18nKey: 'iteratorNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_DIAMOND, fields: [] },
  { type: 'FORK', category: 'flow', color: '#a855f7', shape: 'flow-fork', size: [120, 56], i18nKey: 'forkNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'parallelBranches', i18nKey: 'parallelBranches', input: 'number', defaultValue: 2, inConfig: true },
  ] },
  { type: 'JOIN', category: 'flow', color: '#d946ef', shape: 'flow-join', size: [120, 56], i18nKey: 'joinNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'joinMode', i18nKey: 'joinMode', input: 'select', defaultValue: 'ALL', inConfig: true, options: [
      { value: 'ALL', labelKey: 'joinModeAll' }, { value: 'ANY', labelKey: 'joinModeAny' },
    ] },
  ] },
  { type: 'TRY_CATCH', category: 'flow', color: '#ef4444', shape: 'flow-trycatch', size: [160, 46], i18nKey: 'tryCatchNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'catchStrategy', i18nKey: 'catchStrategy', input: 'select', defaultValue: 'ROUTE_CATCH', inConfig: true, options: [
      { value: 'ROUTE_CATCH', labelKey: 'catchRoute' }, { value: 'PROPAGATE', labelKey: 'catchPropagate' },
    ] },
  ] },
  { type: 'WHILE', category: 'flow', color: '#fb923c', shape: 'flow-while', size: [120, 80], i18nKey: 'whileNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_DIAMOND, fields: [
    { key: 'condition', i18nKey: 'loopCondition', input: 'textarea', placeholder: 'step < 10', inConfig: true },
    { key: 'maxIterations', i18nKey: 'maxIterations', input: 'number', defaultValue: 1000, inConfig: true },
  ] },
  { type: 'TRANSFORMER', category: 'data', color: '#10b981', shape: 'flow-transformer', size: [160, 46], i18nKey: 'transformerNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'mappingExpr', i18nKey: 'mappingExpr', input: 'textarea', placeholder: '{ "total": amount * qty }', inConfig: true },
  ] },
  { type: 'FILTER', category: 'data', color: '#6366f1', shape: 'flow-filter', size: [160, 46], i18nKey: 'filterNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'filterExpr', i18nKey: 'filterExpr', input: 'textarea', placeholder: 'amount > 0', inConfig: true },
  ] },
  { type: 'AGGREGATOR', category: 'data', color: '#f43f5e', shape: 'flow-aggregator', size: [160, 46], i18nKey: 'aggregatorNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'aggregateKey', i18nKey: 'aggregateKey', input: 'text', placeholder: 'items', inConfig: true },
  ] },
  { type: 'SPLITTER', category: 'data', color: '#14b8a6', shape: 'flow-splitter', size: [160, 46], i18nKey: 'splitterNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'splitKey', i18nKey: 'splitKey', input: 'text', placeholder: 'batchItems', inConfig: true },
  ] },
  { type: 'HTTP_CLIENT', category: 'integration', color: '#0ea5e9', shape: 'flow-http', size: [160, 46], i18nKey: 'httpClientNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'httpMethod', i18nKey: 'httpMethod', input: 'select', defaultValue: 'POST', inConfig: true, options: [
      { value: 'GET', labelKey: 'httpGet' }, { value: 'POST', labelKey: 'httpPost' },
      { value: 'PUT', labelKey: 'httpPut' }, { value: 'DELETE', labelKey: 'httpDelete' },
    ] },
    { key: 'httpUrl', i18nKey: 'httpUrl', input: 'text', placeholder: 'https://api.example.com/orders', inConfig: true },
    { key: 'httpBodyTemplate', i18nKey: 'httpBodyTemplate', input: 'textarea', placeholder: '{"orderId": "${orderId}"}', inConfig: true },
  ] },
  { type: 'MQ_PRODUCER', category: 'integration', color: '#f59e0b', shape: 'flow-mq-producer', size: [160, 46], i18nKey: 'mqProducerNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'topic', i18nKey: 'mqTopic', input: 'text', placeholder: 'order.created', inConfig: true },
    { key: 'messageKey', i18nKey: 'mqMessageKey', input: 'text', placeholder: 'orderId', inConfig: true },
  ] },
  { type: 'MQ_CONSUMER', category: 'integration', color: '#10b981', shape: 'flow-mq-consumer', size: [160, 46], i18nKey: 'mqConsumerNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'topic', i18nKey: 'mqTopic', input: 'text', inConfig: true },
    { key: 'consumerGroup', i18nKey: 'mqConsumerGroup', input: 'text', inConfig: true },
  ] },
  { type: 'CACHE_READER', category: 'integration', color: '#eab308', shape: 'flow-cache-reader', size: [160, 46], i18nKey: 'cacheReaderNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'cacheKey', i18nKey: 'cacheKey', input: 'text', placeholder: 'user:${userId}', inConfig: true },
    { key: 'cacheNamespace', i18nKey: 'cacheNamespace', input: 'text', defaultValue: 'default', inConfig: true },
  ] },
  { type: 'CACHE_WRITER', category: 'integration', color: '#a855f7', shape: 'flow-cache-writer', size: [160, 46], i18nKey: 'cacheWriterNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'cacheKey', i18nKey: 'cacheKey', input: 'text', inConfig: true },
    { key: 'cacheTtlSec', i18nKey: 'cacheTtlSec', input: 'number', defaultValue: 3600, inConfig: true },
  ] },
  { type: 'APPROVAL', category: 'human', color: '#ec4899', shape: 'flow-approval', size: [160, 46], i18nKey: 'approvalNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'approvalTitle', i18nKey: 'approvalTitle', input: 'text', inConfig: true },
    { key: 'approverRole', i18nKey: 'approverRole', input: 'text', placeholder: 'manager', inConfig: true },
    { key: 'timeoutHours', i18nKey: 'approvalTimeoutHours', input: 'number', defaultValue: 72, inConfig: true },
  ] },
  { type: 'NOTIFICATION', category: 'human', color: '#06b6d4', shape: 'flow-notification', size: [160, 46], i18nKey: 'notificationNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'channel', i18nKey: 'notifyChannel', input: 'select', defaultValue: 'SMS', inConfig: true, options: [
      { value: 'SMS', labelKey: 'channelSms' }, { value: 'EMAIL', labelKey: 'channelEmail' }, { value: 'WEBHOOK', labelKey: 'channelWebhook' },
    ] },
    { key: 'templateCode', i18nKey: 'templateCode', input: 'text', inConfig: true },
    { key: 'recipients', i18nKey: 'recipients', input: 'text', placeholder: '${mobile}', inConfig: true },
  ] },
  { type: 'LOGGER', category: 'aux', color: '#64748b', shape: 'flow-logger', size: [160, 46], i18nKey: 'loggerNode', bindable: true, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'logLevel', i18nKey: 'logLevel', input: 'select', defaultValue: 'INFO', inConfig: true, options: [
      { value: 'DEBUG', labelKey: 'logDebug' }, { value: 'INFO', labelKey: 'logInfo' }, { value: 'WARN', labelKey: 'logWarn' },
    ] },
    { key: 'logMessage', i18nKey: 'logMessage', input: 'textarea', placeholder: 'order ${orderId} processed', inConfig: true },
  ] },
  { type: 'DELAY', category: 'aux', color: '#94a3b8', shape: 'flow-delay', size: [160, 46], i18nKey: 'delayNode', bindable: false, hasDescription: true, hasScript: false, icon: ICON_TASK, fields: [
    { key: 'delayMs', i18nKey: 'delayMs', input: 'number', defaultValue: 1000, inConfig: true },
  ] },
]

const REGISTRY_MAP = new Map(NODE_TYPE_REGISTRY.map(m => [m.type, m]))

export function getNodeTypeMeta(type?: string | null): NodeTypeMeta | undefined {
  if (!type) return undefined
  const nt = normalizeNodeType(type)
  return REGISTRY_MAP.get(nt)
}

export function paletteNodeTypes(): NodeTypeMeta[] {
  return NODE_TYPE_REGISTRY.filter(m => m.category !== 'terminal')
}

export const PALETTE_CATEGORY_ORDER: NodeCategory[] = ['flow', 'component', 'data', 'integration', 'human', 'aux']

export function paletteNodeTypesByCategory(): { category: NodeCategory; nodes: NodeTypeMeta[] }[] {
  const palette = paletteNodeTypes()
  return PALETTE_CATEGORY_ORDER
    .map(category => ({
      category,
      nodes: palette.filter(m => m.category === category),
    }))
    .filter(g => g.nodes.length > 0)
}

export function defaultNodeFieldValues(meta: NodeTypeMeta): Record<string, string | number> {
  const out: Record<string, string | number> = {}
  meta.fields.forEach(f => {
    if (f.defaultValue !== undefined) out[f.key] = f.defaultValue
    else if (f.input === 'number') out[f.key] = 0
    else out[f.key] = ''
  })
  return out
}

export function extractConfigFromNodeData(nodeType: string, data: Record<string, any>): Record<string, any> {
  const meta = getNodeTypeMeta(nodeType)
  if (!meta) return {}
  const cfg: Record<string, any> = {}
  meta.fields.filter(f => f.inConfig).forEach(f => {
    const v = data[f.key]
    if (v !== undefined && v !== null && v !== '') cfg[f.key] = v
  })
  return cfg
}

export function hydrateNodeDataFromConfig(nodeType: string, data: Record<string, any>, config: Record<string, any> = {}) {
  const meta = getNodeTypeMeta(nodeType)
  if (!meta) return data
  meta.fields.filter(f => f.inConfig).forEach(f => {
    if (config[f.key] !== undefined) data[f.key] = config[f.key]
    else if (data[f.key] === undefined && f.defaultValue !== undefined) data[f.key] = f.defaultValue
  })
  return data
}
