/** AI 提供商字典项 extra 字段结构（与后端 AiProviderDictLoader.buildProviderExtra 对齐） */
export interface AiProviderExtra {
  tier: string
  region: string
  displayNameEn: string
  baseUrl: string
  defaultModel: string
  apiKeyRequired: boolean
  apiKeyPlaceholder: string
  docUrl: string
  tags: string[]
  recommendedFor: string[]
  qualityTier: string
  notes: string
  deprecated: boolean
  successor: string
}

export function emptyAiProviderExtra(): AiProviderExtra {
  return {
    tier: 'B',
    region: 'global',
    displayNameEn: '',
    baseUrl: '',
    defaultModel: '',
    apiKeyRequired: true,
    apiKeyPlaceholder: '',
    docUrl: '',
    tags: [],
    recommendedFor: [],
    qualityTier: 'medium',
    notes: '',
    deprecated: false,
    successor: '',
  }
}

export function parseAiProviderExtra(raw: string | null | undefined): AiProviderExtra {
  const base = emptyAiProviderExtra()
  if (!raw?.trim()) return base
  try {
    const map = JSON.parse(raw) as Record<string, unknown>
    if (map.tier != null) base.tier = String(map.tier)
    if (map.region != null) base.region = String(map.region)
    if (map.displayNameEn != null) base.displayNameEn = String(map.displayNameEn)
    if (map.baseUrl != null) base.baseUrl = String(map.baseUrl)
    if (map.defaultModel != null) base.defaultModel = String(map.defaultModel)
    if (typeof map.apiKeyRequired === 'boolean') base.apiKeyRequired = map.apiKeyRequired
    if (map.apiKeyPlaceholder != null) base.apiKeyPlaceholder = String(map.apiKeyPlaceholder)
    if (map.docUrl != null) base.docUrl = String(map.docUrl)
    if (map.qualityTier != null) base.qualityTier = String(map.qualityTier)
    if (map.notes != null) base.notes = String(map.notes)
    if (map.successor != null) base.successor = String(map.successor)
    if (typeof map.deprecated === 'boolean') base.deprecated = map.deprecated
    if (Array.isArray(map.tags)) base.tags = map.tags.map(String)
    if (Array.isArray(map.recommendedFor)) base.recommendedFor = map.recommendedFor.map(String)
  } catch {
    // 保留默认值，由 UI 覆盖保存时修复
  }
  return base
}

export function serializeAiProviderExtra(extra: AiProviderExtra): string {
  const map: Record<string, unknown> = {
    tier: extra.tier || 'B',
    region: extra.region || 'global',
    displayNameEn: extra.displayNameEn || null,
    baseUrl: extra.baseUrl || null,
    defaultModel: extra.defaultModel || null,
    apiKeyRequired: extra.apiKeyRequired,
    apiKeyPlaceholder: extra.apiKeyPlaceholder || null,
    docUrl: extra.docUrl || null,
    tags: extra.tags,
    recommendedFor: extra.recommendedFor,
    qualityTier: extra.qualityTier || null,
    notes: extra.notes || null,
    deprecated: extra.deprecated || null,
    successor: extra.successor || null,
  }
  return JSON.stringify(map)
}

/** 档位 A/B 对应字典列表 tagType */
export function tagTypeForAiProviderTier(tier: string): string {
  return tier === 'A' ? 'primary' : 'info'
}
