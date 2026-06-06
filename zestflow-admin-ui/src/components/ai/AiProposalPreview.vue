<template>
  <div v-if="summary || chainJson" class="ai-proposal-preview">
    <div class="ai-proposal-header">
      <span class="ai-proposal-title">{{ $t('ai.proposalTitle') }}</span>
      <el-button v-if="chainJson" text size="small" @click="expanded = !expanded">
        {{ expanded ? $t('ai.collapse') : $t('ai.expand') }}
      </el-button>
    </div>
    <p v-if="summary" class="ai-proposal-summary">{{ summary }}</p>
    <el-input
      v-if="chainJson && expanded"
      :model-value="formattedJson"
      type="textarea"
      :rows="8"
      readonly
      class="ai-proposal-json"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  summary?: string | null
  chainJson?: string | null
}>()

const expanded = ref(false)

const formattedJson = computed(() => {
  if (!props.chainJson) return ''
  try {
    const parsed = typeof props.chainJson === 'string' ? JSON.parse(props.chainJson) : props.chainJson
    return JSON.stringify(parsed, null, 2)
  } catch {
    return props.chainJson
  }
})
</script>

<style scoped>
.ai-proposal-preview {
  border-top: 1px solid #ebeef5;
  padding: 12px;
  background: #fafafa;
}

.ai-proposal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ai-proposal-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.ai-proposal-summary {
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.ai-proposal-json :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
