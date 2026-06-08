<template>
  <el-drawer
    v-model="visible"
    :title="$t('ai.title')"
    :size="drawerSize"
    append-to-body
    class="ai-copilot-drawer"
    @open="onOpen"
  >
    <div class="ai-copilot-layout">
      <AiCopilotSessionSidebar
        :sessions="store.sessions"
        :active-session-id="store.sessionId"
        :loading="store.loading"
        @select="handleSelectSession"
        @new-session="handleNewSession"
      />
      <div class="ai-copilot-main">
      <el-alert
        v-if="!enabled"
        :title="$t('ai.notConfigured')"
        type="warning"
        :closable="false"
        show-icon
        class="ai-copilot-alert"
      >
        <template #default>
          <router-link to="/settings/ai" class="ai-settings-link">{{ $t('ai.goSettings') }}</router-link>
        </template>
      </el-alert>

      <el-alert
        v-if="chainKeyHints && hasChainKeyMismatch"
        :title="$t('ai.chainKeyHintsTitle')"
        type="info"
        :closable="false"
        show-icon
        class="ai-copilot-alert"
      >
        <template #default>
          <div v-if="chainKeyHints.declaredNotInAdmin.length" class="chain-key-hint-line">
            {{ $t('ai.declaredNotInAdmin') }}：
            <el-tag v-for="k in chainKeyHints.declaredNotInAdmin" :key="'d-' + k" size="small" type="warning" style="margin:2px">
              {{ k }}
            </el-tag>
          </div>
          <div v-if="chainKeyHints.adminNotDeclared.length" class="chain-key-hint-line">
            {{ $t('ai.adminNotDeclared') }}：
            <el-tag v-for="k in chainKeyHints.adminNotDeclared" :key="'a-' + k" size="small" type="danger" style="margin:2px">
              {{ k }}
            </el-tag>
          </div>
        </template>
      </el-alert>

      <AiMessageList :messages="store.messages" :default-model="store.displayModel" />

      <AiProposalPreview
        :summary="store.pendingSummary"
        :chain-json="store.pendingProposal"
        :current-chain-json="currentChainJson"
        @highlight="emit('highlight-diff', $event)"
      />

      <AiValidationPanel :validation="store.validation" />

      <AiCopilotPlayground
        :enabled="enabled && !!playgroundChainCode"
        :app-code="playgroundAppCode"
        :chain-code="playgroundChainCode"
        @execute-success="handlePlaygroundSuccess"
      />

      <div class="ai-copilot-actions">
        <el-button
          type="primary"
          :disabled="!store.pendingProposal || !enabled"
          @click="handleApply"
        >
          {{ $t('ai.applyToCanvas') }}
        </el-button>
        <el-button
          v-if="playgroundChainCode"
          :disabled="!enabled"
          @click="goPlayground"
        >
          {{ $t('ai.testRun') }}
        </el-button>
        <el-button @click="store.archiveCurrentSession()">{{ $t('ai.sessions.archive') }}</el-button>
      </div>

      <div class="ai-copilot-input">
        <div class="ai-quick-actions">
          <el-button size="small" :disabled="!enabled || store.loading" @click="handleExplain">
            {{ $t('ai.explainChain') }}
          </el-button>
          <el-button
            size="small"
            :disabled="!enabled || store.loading || !store.validation || store.validation.valid"
            @click="handleFixErrors"
          >
            {{ $t('ai.fixErrors') }}
          </el-button>
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          :placeholder="$t('ai.inputPlaceholder')"
          :disabled="!enabled || store.loading"
          @keydown.ctrl.enter.prevent="handleSend"
          @keydown.meta.enter.prevent="handleSend"
        />
        <div class="ai-input-footer">
          <span class="ai-input-hint">{{ $t('ai.sendHint') }}</span>
          <el-button
            type="primary"
            :loading="store.loading"
            :disabled="!enabled || !inputText.trim()"
            @click="handleSend"
          >
            {{ $t('ai.send') }}
          </el-button>
        </div>
      </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAiCopilotStore, type AiCopilotContext } from '@/stores/aiCopilot'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { aiApi, type AiChainKeyHints } from '@/api/ai'
import AiMessageList from './AiMessageList.vue'
import AiProposalPreview from './AiProposalPreview.vue'
import AiValidationPanel from './AiValidationPanel.vue'
import AiCopilotPlayground from './AiCopilotPlayground.vue'
import AiCopilotSessionSidebar from './AiCopilotSessionSidebar.vue'

const props = defineProps<{
  modelValue: boolean
  enabled: boolean
  getContext: () => AiCopilotContext
  playgroundChainCode?: string
  playgroundAppCode?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'apply-proposal': [chainData: string]
  'highlight-diff': [diff: import('@/utils/chainDiff').ChainDiffSummary | null]
}>()

const router = useRouter()
const store = useAiCopilotStore()
const { drawerSize } = useResponsiveDrawerSize(580)
const inputText = ref('')
const chainKeyHints = ref<AiChainKeyHints | null>(null)

const hasChainKeyMismatch = computed(() => {
  if (!chainKeyHints.value) return false
  return chainKeyHints.value.declaredNotInAdmin.length > 0
      || chainKeyHints.value.adminNotDeclared.length > 0
})

const currentChainJson = computed(() => {
  try {
    return getCtx()?.currentChainData ?? null
  } catch {
    return null
  }
})

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

function onOpen() {
  void store.fetchConfig()
  void loadChainKeyHints()
  const ctx = getCtx()
  if (ctx) {
    void store.loadSession(ctx)
  }
}

async function handleSelectSession(id: number) {
  await store.switchSession(id)
}

async function handleNewSession() {
  const ctx = getCtx()
  if (!ctx) return
  store.clearSession()
  await store.createNewSession(ctx)
}

async function loadChainKeyHints() {
  chainKeyHints.value = null
  const ctx = getCtx()
  if (!ctx?.appCode) return
  try {
    chainKeyHints.value = await aiApi.getChainKeyHints(ctx.appCode)
  } catch {
    chainKeyHints.value = null
  }
}

function getCtx(): AiCopilotContext | null {
  try {
    return props.getContext()
  } catch {
    return null
  }
}

async function handleExplain() {
  const ctx = getCtx()
  if (!ctx) return
  await store.sendExplain(ctx, inputText.value.trim() || undefined)
  inputText.value = ''
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  const ctx = getCtx()
  if (!ctx) return
  inputText.value = ''
  await store.sendSuggest(ctx, text, 'modify')
}

async function handleFixErrors() {
  const ctx = getCtx()
  if (!ctx || !store.validation?.errors?.length) return
  const errText = store.validation.errors.join('\n')
  await store.sendSuggest(ctx, errText, 'fix-errors')
}

function handleApply() {
  if (!store.pendingProposal) return
  emit('apply-proposal', store.pendingProposal)
  void store.submitFeedback(true)
  store.clearProposal()
}

function handlePlaygroundSuccess() {
  void store.submitFeedback(false, { playgroundSuccess: true })
}

function goPlayground() {
  if (!props.playgroundChainCode) return
  const ctx = getCtx()
  router.push({
    name: 'Playground',
    query: {
      chainCode: props.playgroundChainCode,
      appCode: ctx?.appCode,
      aiSessionId: store.sessionId || undefined,
      aiFeature: store.lastUserMessage || ctx?.chainCode || undefined,
    },
  })
}
</script>

<style scoped>
.ai-copilot-layout {
  display: flex;
  height: 100%;
  min-height: 0;
}

.ai-copilot-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.ai-copilot-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.ai-copilot-alert {
  margin: 0 12px 8px;
  flex-shrink: 0;
}

.ai-settings-link {
  color: var(--el-color-primary);
  text-decoration: none;
}

.ai-settings-link:hover {
  text-decoration: underline;
}

.ai-copilot-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #ebeef5;
  flex-shrink: 0;
}

.ai-copilot-input {
  padding: 12px;
  border-top: 1px solid #ebeef5;
  flex-shrink: 0;
  background: #fff;
}

.ai-quick-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.ai-input-hint {
  font-size: 11px;
  color: #909399;
}

.chain-key-hint-line {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.8;
}
</style>
