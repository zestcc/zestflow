<template>
  <div ref="listRef" class="ai-message-list">
    <div v-if="messages.length === 0" class="ai-message-empty">
      <el-icon :size="32" color="#c0c4cc"><ChatDotRound /></el-icon>
      <p>{{ $t('ai.emptyHint') }}</p>
    </div>
    <div
      v-for="msg in messages"
      :key="msg.id"
      class="ai-message-item"
      :class="`ai-message-item--${msg.role}`"
    >
      <div class="ai-message-role">
        {{ roleLabel(msg) }}
      </div>
      <div class="ai-message-bubble">
        <template v-if="msg.loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ loadingText(msg) }}</span>
          <div v-if="msg.reasoning" class="ai-message-reasoning ai-message-reasoning--live">
            <div class="ai-message-reasoning-title">{{ $t('ai.reasoningTitle') }}</div>
            <pre class="ai-message-text">{{ msg.reasoning }}</pre>
          </div>
        </template>
        <template v-else>
          <div v-if="msg.reasoning" class="ai-message-reasoning">
            <div class="ai-message-reasoning-title">{{ $t('ai.reasoningTitle') }}</div>
            <pre class="ai-message-text">{{ msg.reasoning }}</pre>
          </div>
          <pre class="ai-message-text">{{ msg.content }}</pre>
          <div v-if="msg.progressSteps?.length" class="ai-message-progress">
            <div
              v-for="(step, idx) in msg.progressSteps"
              :key="idx"
              class="ai-message-progress-item"
            >
              {{ step }}
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import { useDictLabel } from '@/composables/useDictLabel'
import type { AiCopilotMessage } from '@/stores/aiCopilot'

const { t } = useI18n()
const { labelOf: chatRoleLabel } = useDictLabel('ai_chat_role')

const props = defineProps<{
  messages: AiCopilotMessage[]
  defaultModel?: string | null
}>()

const listRef = ref<HTMLElement | null>(null)

function roleLabel(msg: AiCopilotMessage) {
  if (msg.role !== 'assistant') {
    return chatRoleLabel(msg.role)
  }
  const model = msg.model || props.defaultModel
  if (model) {
    return t('ai.roleAssistantWithModel', { model })
  }
  return chatRoleLabel('assistant')
}

function loadingText(msg: AiCopilotMessage) {
  if (msg.progressSteps?.length) {
    const idx = msg.progressIndex ?? 0
    return msg.progressSteps[Math.min(idx, msg.progressSteps.length - 1)]
  }
  return t('ai.thinking')
}

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)

watch(
  () => props.messages.map(m =>
    m.content + String(m.loading) + String(m.progressIndex) + (m.reasoning || ''),
  ).join('|'),
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.ai-message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-message-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  text-align: center;
  gap: 8px;
  padding: 24px;
}

.ai-message-empty p {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}

.ai-message-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-message-item--user {
  align-items: flex-end;
}

.ai-message-item--assistant,
.ai-message-item--system {
  align-items: flex-start;
}

.ai-message-role {
  font-size: 11px;
  color: #909399;
}

.ai-message-bubble {
  max-width: 92%;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.ai-message-item--user .ai-message-bubble {
  background: #ecf5ff;
  color: #303133;
}

.ai-message-item--assistant .ai-message-bubble,
.ai-message-item--system .ai-message-bubble {
  background: #f4f4f5;
  color: #303133;
}

.ai-message-reasoning {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #dcdfe6;
}

.ai-message-reasoning-title {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
}

.ai-message-reasoning--live {
  margin-top: 8px;
  padding-top: 0;
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.ai-message-progress {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.ai-message-progress-item {
  font-size: 11px;
  color: #909399;
  line-height: 1.6;
}

.ai-message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: inherit;
}
</style>
