import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  aiApi,
  isCopilotAvailable,
  type AiConfigStatusVO,
  type AiCopilotMessageVO,
  type AiCopilotSessionDetailVO,
  type AiCopilotSessionSummary,
  type AiExplainResponse,
  type AiSuggestResponse,
  type AiValidationResult,
} from '@/api/ai'
import { streamExplain, streamSuggest } from '@/api/aiStream'

export type AiCopilotMessageRole = 'user' | 'assistant' | 'system'

export interface AiCopilotMessage {
  id: string
  role: AiCopilotMessageRole
  content: string
  reasoning?: string
  model?: string
  timestamp: number
  loading?: boolean
  progressSteps?: string[]
  progressIndex?: number
}

export interface AiCopilotContext {
  designId: string
  chainCode: string
  appCode: string
  currentChainData: string
  graphData: string
}

function nextMessageId() {
  return `msg-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function toSessionIdString(id: string | number | null | undefined): string | null {
  if (id == null) return null
  return String(id)
}

function mapServerMessage(msg: AiCopilotMessageVO, model?: string): AiCopilotMessage {
  return {
    id: `srv-${msg.id}`,
    role: msg.role,
    content: msg.content,
    reasoning: msg.reasoning,
    model,
    timestamp: msg.createdAt ? Date.parse(msg.createdAt) : Date.now(),
  }
}

export const useAiCopilotStore = defineStore('aiCopilot', () => {
  const config = ref<AiConfigStatusVO | null>(null)
  const messages = ref<AiCopilotMessage[]>([])
  const pendingProposal = ref<string | null>(null)
  const pendingSummary = ref<string | null>(null)
  const validation = ref<AiValidationResult | null>(null)
  const sessionId = ref<string | null>(null)
  const repairRounds = ref<number>(0)
  const lastUserMessage = ref<string | null>(null)
  const loading = ref(false)
  const lastContext = ref<AiCopilotContext | null>(null)
  const activeModel = ref<string | null>(null)
  const sessions = ref<AiCopilotSessionSummary[]>([])
  let progressTimer: ReturnType<typeof setInterval> | null = null

  const copilotAvailable = computed(() => isCopilotAvailable(config.value))

  const displayModel = computed(() => activeModel.value || config.value?.model || null)

  async function fetchConfig() {
    try {
      config.value = await aiApi.getConfig()
      if (!activeModel.value && config.value?.model) {
        activeModel.value = config.value.model
      }
    } catch {
      config.value = { globallyEnabled: false, tenantEnabled: false, copilotAvailable: false }
    }
    return config.value
  }

  function stopProgressAnimation() {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
  }

  function startProgressAnimation(msgId: string, steps: string[]) {
    stopProgressAnimation()
    if (!steps.length) return
    const msg = messages.value.find(m => m.id === msgId)
    if (!msg) return
    msg.progressSteps = steps
    msg.progressIndex = 0
    progressTimer = setInterval(() => {
      const target = messages.value.find(m => m.id === msgId)
      if (!target?.loading || !target.progressSteps?.length) {
        stopProgressAnimation()
        return
      }
      target.progressIndex = Math.min(
        (target.progressIndex ?? 0) + 1,
        target.progressSteps.length - 1,
      )
    }, 2500)
  }

  function clearSession() {
    stopProgressAnimation()
    messages.value = []
    pendingProposal.value = null
    pendingSummary.value = null
    validation.value = null
    sessionId.value = null
    repairRounds.value = 0
    lastUserMessage.value = null
  }

  function clearAll() {
    clearSession()
    lastContext.value = null
    sessions.value = []
  }

  function restoreFromServer(detail: AiCopilotSessionDetailVO) {
    sessionId.value = toSessionIdString(detail.sessionId)
    if (detail.model) {
      activeModel.value = detail.model
    }
    messages.value = (detail.messages || []).map(m => mapServerMessage(m, detail.model))
    if (detail.pendingChainData) {
      pendingProposal.value = detail.pendingChainData
      pendingSummary.value = detail.pendingSummary ?? null
      validation.value = detail.pendingValidation ?? null
    }
  }

  async function loadSessionList(context: AiCopilotContext) {
    if (!context.designId || !context.appCode) return
    try {
      sessions.value = await aiApi.listSessions({
        appCode: context.appCode,
        designId: context.designId,
        chainCode: context.chainCode || undefined,
        limit: 20,
      })
    } catch {
      sessions.value = []
    }
  }

  async function loadSession(context: AiCopilotContext) {
    if (!context.designId || !context.appCode) return
    lastContext.value = context
    await loadSessionList(context)
    try {
      const detail = sessionId.value
        ? await aiApi.getSession(sessionId.value)
        : await aiApi.getActiveSession({
            appCode: context.appCode,
            designId: context.designId,
            chainCode: context.chainCode || undefined,
          })
      if (detail?.sessionId) {
        restoreFromServer(detail)
      }
    } catch {
      /* 无历史会话时忽略 */
    }
  }

  async function switchSession(id: number) {
    try {
      const detail = await aiApi.getSession(id)
      restoreFromServer(detail)
    } catch {
      /* ignore */
    }
  }

  async function createNewSession(context: AiCopilotContext) {
    const detail = await aiApi.createSession({
      appCode: context.appCode,
      designId: context.designId,
      chainCode: context.chainCode || undefined,
      mode: 'suggest',
    })
    restoreFromServer(detail)
    await loadSessionList(context)
  }

  async function archiveCurrentSession() {
    if (!sessionId.value || !lastContext.value) return
    try {
      await aiApi.archiveSession(sessionId.value)
      clearSession()
      await loadSessionList(lastContext.value)
      const detail = await aiApi.getActiveSession({
        appCode: lastContext.value.appCode,
        designId: lastContext.value.designId,
        chainCode: lastContext.value.chainCode || undefined,
      })
      if (detail?.sessionId) {
        restoreFromServer(detail)
      }
    } catch {
      /* ignore */
    }
  }

  async function pollJobUntilDone(
    jobId: number,
    assistantMsg: AiCopilotMessage,
    onProgress?: (step: string, reasoning?: string) => void,
  ) {
    const maxAttempts = 120
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise(r => setTimeout(r, 1500))
      const job = await aiApi.getJob(jobId)
      if (job.progressStep) {
        assistantMsg.progressSteps = [job.progressStep]
        onProgress?.(job.progressStep, job.reasoning)
      }
      if (job.reasoning) {
        assistantMsg.reasoning = job.reasoning
      }
      if (job.status === 'DONE') {
        if (job.suggestResult) {
          applySuggestResponse(job.suggestResult)
          finishLoadingMessage(assistantMsg.id, job.suggestResult.summary, {
            reasoning: job.suggestResult.reasoning || assistantMsg.reasoning,
            model: job.suggestResult.model,
            progressSteps: job.suggestResult.progressSteps,
          })
        } else if (job.explainResult) {
          finishLoadingMessage(assistantMsg.id, job.explainResult.explanation, {
            model: job.explainResult.model,
          })
          sessionId.value = toSessionIdString(job.explainResult.sessionId) ?? sessionId.value
        }
        return
      }
      if (job.status === 'FAILED' || job.status === 'CANCELLED') {
        finishLoadingMessage(assistantMsg.id, job.errorMessage || 'Error')
        return
      }
    }
    finishLoadingMessage(assistantMsg.id, 'Job timeout')
  }

  function appendMessage(role: AiCopilotMessageRole, content: string, loadingFlag = false) {
    const msg: AiCopilotMessage = {
      id: nextMessageId(),
      role,
      content,
      model: role === 'assistant' ? displayModel.value || undefined : undefined,
      timestamp: Date.now(),
      loading: loadingFlag,
    }
    messages.value.push(msg)
    return msg
  }

  function finishLoadingMessage(
    msgId: string,
    content: string,
    extra?: { reasoning?: string; model?: string; progressSteps?: string[] },
  ) {
    stopProgressAnimation()
    const msg = messages.value.find(m => m.id === msgId)
    if (msg) {
      msg.content = content
      msg.reasoning = extra?.reasoning
      msg.model = extra?.model || displayModel.value || undefined
      msg.loading = false
      msg.progressSteps = extra?.progressSteps
      msg.progressIndex = undefined
    }
  }

  function applySuggestResponse(res: AiSuggestResponse) {
    pendingProposal.value = res.proposedChainData
    pendingSummary.value = res.summary
    validation.value = res.validation
    sessionId.value = toSessionIdString(res.sessionId) ?? sessionId.value
    repairRounds.value = res.repairRounds ?? 0
    if (res.model) {
      activeModel.value = res.model
    }
  }

  function setPendingProposal(chainData: string, summary?: string | null) {
    pendingProposal.value = chainData
    pendingSummary.value = summary ?? null
    validation.value = null
  }

  async function sendExplain(context: AiCopilotContext, userMessage?: string) {
    lastContext.value = context
    const prompt = userMessage?.trim() || ''
    appendMessage('user', prompt || '[explain]')
    const assistantMsg = appendMessage('assistant', '', true)
    loading.value = true
    let streamed = ''
    try {
      await streamExplain({
        designId: context.designId,
        chainCode: context.chainCode,
        appCode: context.appCode,
        currentChainData: context.currentChainData,
        graphData: context.graphData,
        userMessage: prompt || undefined,
        sessionId: sessionId.value || undefined,
      }, (ev) => {
        if (ev.type === 'progress') {
          assistantMsg.progressSteps = [ev.step]
          assistantMsg.progressIndex = 0
        } else if (ev.type === 'content') {
          streamed += ev.delta
          assistantMsg.content = streamed
        } else if (ev.type === 'reasoning') {
          assistantMsg.reasoning = (assistantMsg.reasoning || '') + ev.delta
        } else if (ev.type === 'done') {
          const res = ev.payload as AiExplainResponse
          finishLoadingMessage(assistantMsg.id, res.explanation, { model: res.model })
          sessionId.value = toSessionIdString(res.sessionId) ?? sessionId.value
          if (res.model) activeModel.value = res.model
        } else if (ev.type === 'error') {
          finishLoadingMessage(assistantMsg.id, ev.message)
        }
      })
    } catch (e: any) {
      try {
        const job = await aiApi.submitExplainJob({
          designId: context.designId,
          chainCode: context.chainCode,
          appCode: context.appCode,
          currentChainData: context.currentChainData,
          graphData: context.graphData,
          userMessage: prompt || undefined,
          sessionId: sessionId.value || undefined,
        })
        await pollJobUntilDone(job.jobId, assistantMsg)
      } catch {
        finishLoadingMessage(assistantMsg.id, e?.message || 'Error')
      }
    } finally {
      loading.value = false
    }
  }

  async function sendSuggest(
    context: AiCopilotContext,
    userMessage: string,
    mode: 'generate' | 'modify' | 'fix-errors' = 'modify',
  ) {
    lastContext.value = context
    lastUserMessage.value = userMessage
    appendMessage('user', userMessage)
    const assistantMsg = appendMessage('assistant', '', true)
    loading.value = true
    const progressSteps: string[] = []
    let streamedSummary = ''
    try {
      await streamSuggest({
        designId: context.designId,
        chainCode: context.chainCode,
        appCode: context.appCode,
        currentChainData: context.currentChainData,
        graphData: context.graphData,
        userMessage,
        mode,
        sessionId: sessionId.value || undefined,
      }, (ev) => {
        if (ev.type === 'progress') {
          progressSteps.push(ev.step)
          assistantMsg.progressSteps = [...progressSteps]
          assistantMsg.progressIndex = progressSteps.length - 1
        } else if (ev.type === 'reasoning') {
          assistantMsg.reasoning = (assistantMsg.reasoning || '') + ev.delta
        } else if (ev.type === 'content') {
          streamedSummary += ev.delta
        } else if (ev.type === 'done') {
          const res = ev.payload as AiSuggestResponse
          applySuggestResponse(res)
          finishLoadingMessage(assistantMsg.id, res.summary, {
            reasoning: res.reasoning || assistantMsg.reasoning,
            model: res.model,
            progressSteps: res.progressSteps || progressSteps,
          })
        } else if (ev.type === 'error') {
          finishLoadingMessage(assistantMsg.id, ev.message)
          pendingProposal.value = null
          pendingSummary.value = null
          validation.value = null
        }
      })
    } catch (e: any) {
      try {
        const job = await aiApi.submitSuggestJob({
          designId: context.designId,
          chainCode: context.chainCode,
          appCode: context.appCode,
          currentChainData: context.currentChainData,
          graphData: context.graphData,
          userMessage,
          mode,
          sessionId: sessionId.value || undefined,
        })
        await pollJobUntilDone(job.jobId, assistantMsg)
      } catch {
        finishLoadingMessage(assistantMsg.id, e?.message || 'Error')
        pendingProposal.value = null
        pendingSummary.value = null
        validation.value = null
      }
    } finally {
      loading.value = false
    }
  }

  async function sendExpressionSuggest(
    context: AiCopilotContext,
    userMessage: string,
    currentExpression?: string,
  ) {
    lastContext.value = context
    appendMessage('user', userMessage)
    const assistantMsg = appendMessage('assistant', '', true)
    loading.value = true
    try {
      const res = await aiApi.suggestExpression({
        appCode: context.appCode,
        designId: context.designId,
        chainCode: context.chainCode,
        currentExpression,
        userMessage,
        context: context.currentChainData,
      })
      const text = res.explanation
        ? `${res.expression}\n\n${res.explanation}`
        : res.expression
      finishLoadingMessage(assistantMsg.id, text)
      sessionId.value = toSessionIdString(res.sessionId) ?? sessionId.value
    } catch (e: any) {
      finishLoadingMessage(assistantMsg.id, e?.message || 'Error')
    } finally {
      loading.value = false
    }
  }

  async function revalidateProposal(appCode: string) {
    if (!pendingProposal.value) return
    loading.value = true
    try {
      const res = await aiApi.validate({ appCode, chainData: pendingProposal.value })
      validation.value = res.validation
    } finally {
      loading.value = false
    }
  }

  async function submitFeedback(adopted: boolean, extra?: Partial<import('@/api/ai').AiFeedbackRequest>) {
    if (!sessionId.value) return
    try {
      await aiApi.submitFeedback(sessionId.value, {
        adopted,
        intent: 'COMPOSE_CHAIN',
        feature: lastUserMessage.value || lastContext.value?.chainCode || undefined,
        validatePassed: validation.value?.valid,
        validateRounds: repairRounds.value,
        chainData: pendingProposal.value || undefined,
        ...extra,
      })
    } catch { /* ignore */ }
  }

  function clearProposal() {
    pendingProposal.value = null
    pendingSummary.value = null
    validation.value = null
  }

  return {
    config,
    messages,
    pendingProposal,
    pendingSummary,
    validation,
    sessionId,
    repairRounds,
    lastUserMessage,
    loading,
    lastContext,
    activeModel,
    displayModel,
    sessions,
    copilotAvailable,
    fetchConfig,
    loadSession,
    loadSessionList,
    switchSession,
    createNewSession,
    archiveCurrentSession,
    clearSession,
    clearAll,
    appendMessage,
    sendExplain,
    sendSuggest,
    sendExpressionSuggest,
    revalidateProposal,
    submitFeedback,
    clearProposal,
    applySuggestResponse,
    setPendingProposal,
  }
})
