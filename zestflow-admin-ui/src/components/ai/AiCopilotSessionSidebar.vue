<template>
  <div class="ai-session-sidebar">
    <div class="ai-session-toolbar">
      <el-button size="small" type="primary" :disabled="loading" @click="emit('new-session')">
        {{ $t('ai.sessions.new') }}
      </el-button>
    </div>
    <el-scrollbar class="ai-session-list">
      <div
        v-for="item in sessions"
        :key="item.sessionId"
        class="ai-session-item"
        :class="{ active: String(item.sessionId) === String(activeSessionId ?? '') }"
        @click="emit('select', item.sessionId)"
      >
        <div class="ai-session-title">
          {{ item.title || $t('ai.sessions.untitled') }}
        </div>
        <div class="ai-session-meta">
          <el-tag v-if="item.hasPending" size="small" type="warning">{{ $t('ai.sessions.pending') }}</el-tag>
          <span>{{ item.messageCount ?? 0 }} {{ $t('ai.sessions.messages') }}</span>
        </div>
      </div>
      <el-empty v-if="!sessions.length" :description="$t('ai.sessions.empty')" :image-size="48" />
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import type { AiCopilotSessionSummary } from '@/api/ai'

defineProps<{
  sessions: AiCopilotSessionSummary[]
  activeSessionId: string | null
  loading?: boolean
}>()

const emit = defineEmits<{
  select: [sessionId: number]
  'new-session': []
}>()
</script>

<style scoped>
.ai-session-sidebar {
  display: flex;
  flex-direction: column;
  width: 168px;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
}

.ai-session-toolbar {
  padding: 8px;
  border-bottom: 1px solid #ebeef5;
}

.ai-session-list {
  flex: 1;
  min-height: 0;
}

.ai-session-item {
  padding: 8px 10px;
  cursor: pointer;
  border-bottom: 1px solid #f0f2f5;
}

.ai-session-item:hover {
  background: #f0f7ff;
}

.ai-session-item.active {
  background: #ecf5ff;
  border-left: 3px solid var(--el-color-primary);
}

.ai-session-title {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-session-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #909399;
}
</style>
