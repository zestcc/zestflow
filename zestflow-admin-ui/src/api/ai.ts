import http from './index'

export interface AiConfigStatusVO {
  globallyEnabled: boolean
  tenantEnabled: boolean
  copilotAvailable: boolean
  preset?: string
  model?: string
  presetDisplayName?: string
}

/** 与 {@link AiConfigStatusVO} 同义，兼容旧引用 */
export type AiConfigVO = AiConfigStatusVO

export function isCopilotAvailable(cfg: AiConfigStatusVO | null | undefined): boolean {
  return cfg?.copilotAvailable === true
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
  /** 与后端 apiKeyConfigured 对齐 */
  apiKeyConfigured?: boolean
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
  sessionId?: string | number
}

export interface AiExplainRequest extends AiDesignContextRequest {
  userMessage?: string
}

export interface AiExplainResponse {
  explanation: string
  sessionId?: string | number
  model?: string
}

export interface AiSuggestRequest extends AiDesignContextRequest {
  userMessage: string
  mode?: 'generate' | 'modify' | 'fix-errors'
}

export interface AiSuggestResponse {
  proposedChainData: string
  summary: string
  reasoning?: string
  validation: AiValidationResult
  sessionId?: string | number
  repairRounds?: number
  model?: string
  progressSteps?: string[]
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

export interface AiFeedbackRequest {
  adopted: boolean
  comment?: string
  intent?: string
  feature?: string
  validatePassed?: boolean
  validateRounds?: number
  playgroundSuccess?: boolean
  chainData?: string
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

export interface AiCopilotMessageVO {
  id: number
  role: 'user' | 'assistant' | 'system'
  content: string
  reasoning?: string
  createdAt?: string
}

export interface AiCopilotSessionDetailVO {
  sessionId: number
  title?: string
  mode?: string
  model?: string
  messages: AiCopilotMessageVO[]
  pendingChainData?: string
  pendingSummary?: string
  pendingValidation?: AiValidationResult
}

export interface AiCopilotSessionSummary {
  sessionId: number
  title?: string
  mode?: string
  lastModel?: string
  success?: boolean
  latencyMs?: number
  messageCount?: number
  hasPending?: boolean
  lastMessagePreview?: string
  createdAt?: string
}

export interface AiCopilotTraceStep {
  id: number
  sessionId: number
  jobId?: number
  stepType: string
  stepName: string
  status: string
  latencyMs?: number
  tokenEstimate?: number
  detailJson?: string
  sortOrder?: number
  createdAt?: string
}

export interface AiCopilotTraceOverview {
  days: number
  totalSteps: number
  failedSteps: number
  avgStepLatencyMs: number
  stepsByType?: Record<string, number>
  recentSessions?: Array<{
    sessionId: number
    title?: string
    mode?: string
    appCode?: string
    designId?: string
    stepCount?: number
    totalLatencyMs?: number
    success?: boolean
    createdAt?: string
  }>
}

export interface AiLearningEvent {
  id: number
  appCode?: string
  intent?: string
  feature?: string
  chainCode?: string
  httpMode?: number
  validatePassed?: boolean
  validateRounds?: number
  adopted?: boolean
  playgroundSuccess?: boolean
  promotionScore?: number
  promotionEligible?: boolean
  userCorrection?: string
  promotedToRag?: boolean
  createdAt?: string
}

export interface AiCopilotJob {
  jobId: number
  jobType: string
  status: string
  sessionId?: number
  progressStep?: string
  reasoning?: string
  suggestResult?: AiSuggestResponse
  explainResult?: AiExplainResponse
  errorMessage?: string
  latencyMs?: number
  createdAt?: string
  finishedAt?: string
}

/** Copilot 长耗时请求超时（毫秒），须大于后端 LLM timeout */
const AI_LONG_TIMEOUT_MS = 120_000

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

export interface ExecutorRagStatus {
  storage?: string
  eventCount?: number
  patternCount?: number
  patterns?: Array<{
    id: string
    title: string
    feature: string
    confidence: number
    sampleCount: number
  }>
  lastEventAt?: string
  lastFeature?: string
  error?: string
}

export interface AiRagStatus {
  enabled?: boolean
  mode?: string
  platformChunks?: number
  tenantChunks?: number
  tenantDocuments?: number
  filesystemPath?: string
  tenantRagAutoPromote?: boolean
  primaryKnowledgeBase?: string
  executor?: ExecutorRagStatus
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
    return http.get<AiConfigStatusVO>('/ai/config')
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
    return http.post<AiExplainResponse>('/ai/design/explain', data, { timeout: AI_LONG_TIMEOUT_MS })
  },

  getActiveSession(params: { appCode: string; designId: string; chainCode?: string }) {
    return http.get<AiCopilotSessionDetailVO | null>('/ai/sessions/active', { params })
  },

  listSessions(params: { appCode: string; designId: string; chainCode?: string; limit?: number }) {
    return http.get<AiCopilotSessionSummary[]>('/ai/sessions', { params })
  },

  getSession(id: number | string) {
    return http.get<AiCopilotSessionDetailVO>(`/ai/sessions/${id}`)
  },

  createSession(data: {
    appCode: string
    designId: string
    chainCode?: string
    title?: string
    mode?: string
  }) {
    return http.post<AiCopilotSessionDetailVO>('/ai/sessions', data)
  },

  updateSession(id: number | string, data: { title?: string }) {
    return http.put<AiCopilotSessionDetailVO>(`/ai/sessions/${id}`, data)
  },

  archiveSession(id: number | string) {
    return http.delete<void>(`/ai/sessions/${id}`)
  },

  getSessionTrace(id: number | string) {
    return http.get<AiCopilotTraceStep[]>(`/ai/sessions/${id}/trace`)
  },

  getTraceOverview(days = 30) {
    return http.get<AiCopilotTraceOverview>('/ai/trace/overview', { params: { days } })
  },

  submitSuggestJob(data: AiSuggestRequest) {
    return http.post<AiCopilotJob>('/ai/jobs/suggest', data)
  },

  submitExplainJob(data: AiExplainRequest) {
    return http.post<AiCopilotJob>('/ai/jobs/explain', data)
  },

  getJob(id: number | string) {
    return http.get<AiCopilotJob>(`/ai/jobs/${id}`)
  },

  cancelJob(id: number | string) {
    return http.post<void>(`/ai/jobs/${id}/cancel`)
  },

  listLearningEvents(appCode?: string, limit = 30) {
    return http.get<AiLearningEvent[]>('/ai/learning/events', { params: { appCode, limit } })
  },

  promoteLearningEvent(id: number) {
    return http.post<AiRagDocument>(`/ai/learning/events/${id}/promote-rag`)
  },

  suggest(data: AiSuggestRequest) {
    return http.post<AiSuggestResponse>('/ai/design/suggest', data, { timeout: AI_LONG_TIMEOUT_MS })
  },

  validate(data: AiValidateRequest) {
    return http.post<AiValidateResponse>('/ai/design/validate', data)
  },

  suggestExpression(data: AiExpressionSuggestRequest) {
    return http.post<AiExpressionSuggestResponse>('/ai/expression/suggest', data, { timeout: AI_LONG_TIMEOUT_MS })
  },

  diagnose(data: AiDiagnoseRequest) {
    return http.post<AiDiagnoseResponse>('/ai/logs/diagnose', data, { timeout: AI_LONG_TIMEOUT_MS })
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

  getRagStatus(appCode?: string) {
    return http.get<AiRagStatus>('/ai/rag/status', { params: appCode ? { appCode } : {} })
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

  listDeliveryPatterns() {
    return http.get<Array<{ id: string; title: string; topology: string }>>('/ai/delivery/patterns')
  },

  composeChain(data: {
    appCode: string
    patternId?: string
    chainCode: string
    chainName?: string
    componentBindings?: Record<string, string>
  }) {
    return http.post<Record<string, unknown>>('/ai/chains/compose', data)
  },

  validateDelivery(data: {
    appCode: string
    chainCode?: string
    chainData?: string
    graphData?: string
    projectRoot?: string
    strictMode?: boolean
  }) {
    return http.post<{
      passed: boolean
      blocking?: string[]
      warnings?: string[]
      next_actions?: string[]
    }>('/ai/delivery/validate', data)
  },
}
