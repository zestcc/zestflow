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
        {{ msg.role === 'user' ? $t('ai.roleUser') : $t('ai.roleAssistant') }}
      </div>
      <div class="ai-message-bubble">
        <template v-if="msg.loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          {{ $t('ai.thinking') }}
        </template>
        <pre v-else class="ai-message-text">{{ msg.content }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import type { AiCopilotMessage } from '@/stores/aiCopilot'

const props = defineProps<{
  messages: AiCopilotMessage[]
}>()

const listRef = ref<HTMLElement | null>(null)

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
  () => props.messages.map(m => m.content + String(m.loading)).join('|'),
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

.ai-message-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: inherit;
}
</style>
