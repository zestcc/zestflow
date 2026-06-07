import http from './index'

export interface AiConfigVO {
  enabled: boolean
  configured: boolean
  preset?: string
  model?: string
  globalEnabled?: boolean
  message?: string
}

export interface AiProviderPreset {
  id: string
  displayName: string
  displayNameEn: string
  tier: 'A' | 'B'
  region: 'cn' | 'global' | 'local'
  baseUrl: string
  defaultModel: string
  models: string[]
  apiKeyRequired: boolean
  apiKeyPlaceholder?: string
  docUrl?: string
  tags?: string[]
  recommendedFor?: string[]
  qualityTier?: string
  notes?: string
  deprecated?: boolean
  successor?: string
}

export interface AiProvidersResponse {
  version?: string
  presets: AiProviderPreset[]
}

export interface AiTenantConfigVO {
  enabled: boolean
  preset: string
  baseUrl?: string
  apiKeyMasked?: string
  hasApiKey?: boolean
  model?: string
  allowedPresets?: string[]
}

export interface AiTenantConfigDTO {
  enabled: boolean
  preset: string
  baseUrl?: string
  apiKey?: string
  model?: string
  allowedPresets?: string[]
}

export interface AiTestRequest {
  preset: string
  baseUrl?: string
  apiKey?: string
  model: string
}

export interface AiTestResponse {
  success: boolean
  latencyMs?: number
  model?: string
  message?: string
}

export interface AiValidationResult {
  valid: boolean
  errors: string[]
}

export interface AiDesignContextRequest {
  designId: string
  chainCode?: string
  appCode: string
  currentChainData?: string
  graphData?: string
  userMessage?: string
}

export interface AiExplainRequest extends AiDesignContextRequest {
  userMessage?: string
}

export interface AiExplainResponse {
  explanation: string
  sessionId?: string
}

export interface AiSuggestRequest extends AiDesignContextRequest {
  userMessage: string
  mode?: 'generate' | 'modify' | 'fix-errors'
}

export interface AiSuggestResponse {
  proposedChainData: string
  summary: string
  validation: AiValidationResult
  sessionId?: string
}

export interface AiValidateRequest {
  appCode: string
  chainData: string
}

export interface AiValidateResponse {
  validation: AiValidationResult
}

export interface AiExpressionSuggestRequest {
  appCode: string
  designId?: string
  chainCode?: string
  currentExpression?: string
  userMessage: string
  context?: string
}

export interface AiExpressionSuggestResponse {
  expression: string
  explanation?: string
  sessionId?: string
}

export interface AiComponentParam {
  name: string
  type: string
  required?: boolean
}

export interface AiComponentScaffoldRequest {
  appCode: string
  componentId: string
  componentType: string
  groupName?: string
  description: string
  inputParams?: AiComponentParam[]
  outputParams?: AiComponentParam[]
}

export interface AiComponentScaffoldResponse {
  fullJavaCode: string
  summary: string
  checklist: string[]
  sessionId?: string
}

export interface AiFeedbackRequest {
  adopted: boolean
  comment?: string
}

export interface AiDiagnoseRequest {
  appCode: string
  chainCode?: string
  executionId?: string
  traceId?: string
  errorSummary?: string
  designId?: string
}

export interface AiDiagnoseResponse {
  diagnosis: string
  suggestion: string
  stub?: boolean
  sessionId?: string
  openDesignPath?: string
}

export interface AiChainKeyHints {
  declaredKeys: string[]
  adminKeys: string[]
  declaredNotInAdmin: string[]
  adminNotDeclared: string[]
}

export interface AiChainTemplate {
  id: number
  name: string
  description?: string
  appCode?: string
  promptSummary?: string
  chainData: string
  createdBy?: string
  createdAt?: string
}

export interface AiChainTemplateSaveDTO {
  name: string
  description?: string
  appCode?: string
  promptSummary?: string
  chainData: string
}

export interface AiRagDocument {
  id: number
  title: string
  appCode?: string
  content: string
  enabled?: boolean
  sortOrder?: number
  sourceType?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface AiRagDocumentSaveDTO {
  title: string
  appCode?: string
  content: string
  enabled?: boolean
  sortOrder?: number
}

export interface AiRagStatus {
  enabled?: boolean
  mode?: string
  platformChunks?: number
  tenantChunks?: number
  tenantDocuments?: number
  filesystemPath?: string
}

export interface AiUsageDaily {
  date: string
  sessions: number
  successSessions: number
}

export interface AiUsageOverview {
  days: number
  totalSessions: number
  successSessions: number
  successRate: number
  avgLatencyMs: number
  totalTokenEstimate: number
  adoptedCount: number
  feedbackCount: number
  adoptedRate: number
  sessionsByMode?: Record<string, number>
  dailyTrend?: AiUsageDaily[]
}

export interface AiComponentContextItem {
  componentId: string
  componentName?: string
  componentType?: string
  groupName?: string
}

export const aiApi = {
  getConfig() {
    return http.get<AiConfigVO>('/ai/config')
  },

  getProviders() {
    return http.get<AiProviderPreset[]>('/ai/providers')
  },

  testConnection(data: AiTestRequest) {
    return http.post<AiTestResponse>('/ai/test', data)
  },

  getTenantConfig() {
    return http.get<AiTenantConfigVO>('/ai/tenant-config')
  },

  saveTenantConfig(data: AiTenantConfigDTO) {
    return http.put<AiTenantConfigVO>('/ai/tenant-config', data)
  },

  getContextComponents(appCode: string) {
    return http.get<AiComponentContextItem[]>('/ai/context/components', { params: { appCode } })
  },

  explain(data: AiExplainRequest) {
    return http.post<AiExplainResponse>('/ai/design/explain', data)
  },

  suggest(data: AiSuggestRequest) {
    return http.post<AiSuggestResponse>('/ai/design/suggest', data)
  },

  validate(data: AiValidateRequest) {
    return http.post<AiValidateResponse>('/ai/design/validate', data)
  },

  suggestExpression(data: AiExpressionSuggestRequest) {
    return http.post<AiExpressionSuggestResponse>('/ai/expression/suggest', data)
  },

  scaffoldComponent(data: AiComponentScaffoldRequest) {
    return http.post<AiComponentScaffoldResponse>('/ai/component/scaffold', data)
  },

  diagnose(data: AiDiagnoseRequest) {
    return http.post<AiDiagnoseResponse>('/ai/logs/diagnose', data)
  },

  getChainKeyHints(appCode: string) {
    return http.get<AiChainKeyHints>('/ai/context/chain-keys', { params: { appCode } })
  },

  listTemplates(appCode?: string) {
    return http.get<AiChainTemplate[]>('/ai/templates', { params: appCode ? { appCode } : {} })
  },

  getTemplate(id: number) {
    return http.get<AiChainTemplate>(`/ai/templates/${id}`)
  },

  saveTemplate(data: AiChainTemplateSaveDTO) {
    return http.post<AiChainTemplate>('/ai/templates', data)
  },

  deleteTemplate(id: number) {
    return http.delete<void>(`/ai/templates/${id}`)
  },

  ragSearch(q: string, limit = 3, appCode?: string) {
    return http.get<string[]>('/ai/rag/search', { params: { q, limit, appCode } })
  },

  getRagStatus() {
    return http.get<AiRagStatus>('/ai/rag/status')
  },

  listRagDocuments(appCode?: string) {
    return http.get<AiRagDocument[]>('/ai/rag/documents', { params: appCode ? { appCode } : {} })
  },

  getRagDocument(id: number) {
    return http.get<AiRagDocument>(`/ai/rag/documents/${id}`)
  },

  saveRagDocument(data: AiRagDocumentSaveDTO) {
    return http.post<AiRagDocument>('/ai/rag/documents', data)
  },

  updateRagDocument(id: number, data: AiRagDocumentSaveDTO) {
    return http.put<AiRagDocument>(`/ai/rag/documents/${id}`, data)
  },

  deleteRagDocument(id: number) {
    return http.delete<void>(`/ai/rag/documents/${id}`)
  },

  rebuildRagIndex() {
    return http.post<void>('/ai/rag/documents/rebuild-index')
  },

  getUsageOverview(days = 30) {
    return http.get<AiUsageOverview>('/ai/usage/overview', { params: { days } })
  },

  submitFeedback(sessionId: string, data: AiFeedbackRequest) {
    return http.post<void>(`/ai/sessions/${sessionId}/feedback`, data)
  },
}
