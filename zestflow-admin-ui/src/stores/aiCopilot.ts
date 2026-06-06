import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  aiApi,
  type AiConfigVO,
  type AiSuggestResponse,
  type AiValidationResult,
} from '@/api/ai'

export type AiCopilotMessageRole = 'user' | 'assistant' | 'system'

export interface AiCopilotMessage {
  id: string
  role: AiCopilotMessageRole
  content: string
  timestamp: number
  loading?: boolean
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

export const useAiCopilotStore = defineStore('aiCopilot', () => {
  const config = ref<AiConfigVO | null>(null)
  const messages = ref<AiCopilotMessage[]>([])
  const pendingProposal = ref<string | null>(null)
  const pendingSummary = ref<string | null>(null)
  const validation = ref<AiValidationResult | null>(null)
  const sessionId = ref<string | null>(null)
  const loading = ref(false)
  const lastContext = ref<AiCopilotContext | null>(null)

  const copilotAvailable = computed(() => {
    if (!config.value) return false
    return config.value.globalEnabled !== false && config.value.enabled && config.value.configured
  })

  async function fetchConfig() {
    try {
      config.value = await aiApi.getConfig()
    } catch {
      config.value = { enabled: false, configured: false }
    }
    return config.value
  }

  function clearSession() {
    messages.value = []
    pendingProposal.value = null
    pendingSummary.value = null
    validation.value = null
    sessionId.value = null
    lastContext.value = null
  }

  function appendMessage(role: AiCopilotMessageRole, content: string, loadingFlag = false) {
    const msg: AiCopilotMessage = {
      id: nextMessageId(),
      role,
      content,
      timestamp: Date.now(),
      loading: loadingFlag,
    }
    messages.value.push(msg)
    return msg
  }

  function finishLoadingMessage(msgId: string, content: string) {
    const msg = messages.value.find(m => m.id === msgId)
    if (msg) {
      msg.content = content
      msg.loading = false
    }
  }

  function applySuggestResponse(res: AiSuggestResponse) {
    pendingProposal.value = res.proposedChainData
    pendingSummary.value = res.summary
    validation.value = res.validation
    sessionId.value = res.sessionId ?? null
  }

  async function sendExplain(context: AiCopilotContext, userMessage?: string) {
    lastContext.value = context
    const prompt = userMessage?.trim() || ''
    const userMsg = appendMessage('user', prompt || '[explain]')
    const assistantMsg = appendMessage('assistant', '', true)
    loading.value = true
    try {
      const res = await aiApi.explain({
        designId: context.designId,
        chainCode: context.chainCode,
        appCode: context.appCode,
        currentChainData: context.currentChainData,
        graphData: context.graphData,
        userMessage: prompt || undefined,
      })
      finishLoadingMessage(assistantMsg.id, res.explanation)
      sessionId.value = res.sessionId ?? sessionId.value
    } catch (e: any) {
      finishLoadingMessage(assistantMsg.id, e?.message || 'Error')
    } finally {
      loading.value = false
      void userMsg
    }
  }

  async function sendSuggest(
    context: AiCopilotContext,
    userMessage: string,
    mode: 'generate' | 'modify' | 'fix-errors' = 'modify',
  ) {
    lastContext.value = context
    appendMessage('user', userMessage)
    const assistantMsg = appendMessage('assistant', '', true)
    loading.value = true
    try {
      const res = await aiApi.suggest({
        designId: context.designId,
        chainCode: context.chainCode,
        appCode: context.appCode,
        currentChainData: context.currentChainData,
        graphData: context.graphData,
        userMessage,
        mode,
      })
      applySuggestResponse(res)
      finishLoadingMessage(assistantMsg.id, res.summary)
    } catch (e: any) {
      finishLoadingMessage(assistantMsg.id, e?.message || 'Error')
      pendingProposal.value = null
      pendingSummary.value = null
      validation.value = null
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
      sessionId.value = res.sessionId ?? sessionId.value
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

  async function submitFeedback(adopted: boolean, comment?: string) {
    if (!sessionId.value) return
    try {
      await aiApi.submitFeedback(sessionId.value, { adopted, comment })
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
    loading,
    lastContext,
    copilotAvailable,
    fetchConfig,
    clearSession,
    appendMessage,
    sendExplain,
    sendSuggest,
    sendExpressionSuggest,
    revalidateProposal,
    submitFeedback,
    clearProposal,
    applySuggestResponse,
  }
})
