<template>
  <div v-if="summary || chainJson" class="ai-proposal-preview">
    <div class="ai-proposal-header">
      <span class="ai-proposal-title">{{ $t('ai.proposalTitle') }}</span>
      <el-button v-if="chainJson" text size="small" @click="expanded = !expanded">
        {{ expanded ? $t('ai.collapse') : $t('ai.expand') }}
      </el-button>
    </div>
    <p v-if="summary" class="ai-proposal-summary">{{ summary }}</p>

    <div v-if="diff && hasDiff" class="ai-proposal-diff">
      <div class="diff-title">{{ $t('ai.diffTitle') }}</div>
      <div v-if="diff.nodesAdded.length" class="diff-row">
        <el-tag type="success" size="small">{{ $t('ai.diffNodesAdded') }}</el-tag>
        <span>{{ diff.nodesAdded.join(', ') }}</span>
      </div>
      <div v-if="diff.nodesRemoved.length" class="diff-row">
        <el-tag type="danger" size="small">{{ $t('ai.diffNodesRemoved') }}</el-tag>
        <span>{{ diff.nodesRemoved.join(', ') }}</span>
      </div>
      <div v-if="diff.nodesChanged.length" class="diff-row">
        <el-tag type="warning" size="small">{{ $t('ai.diffNodesChanged') }}</el-tag>
        <span>{{ diff.nodesChanged.join(', ') }}</span>
      </div>
      <div v-if="diff.edgesAdded > 0" class="diff-row">
        <el-tag type="success" size="small">{{ $t('ai.diffEdgesAdded') }}</el-tag>
        <span>+{{ diff.edgesAdded }}</span>
      </div>
      <div v-if="diff.edgesRemoved > 0" class="diff-row">
        <el-tag type="danger" size="small">{{ $t('ai.diffEdgesRemoved') }}</el-tag>
        <span>-{{ diff.edgesRemoved }}</span>
      </div>
    </div>

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
import { ref, computed, watch } from 'vue'
import { computeChainDiff, hasChainDiff as checkHasDiff, type ChainDiffSummary } from '@/utils/chainDiff'

const props = defineProps<{
  summary?: string | null
  chainJson?: string | null
  currentChainJson?: string | null
}>()

const emit = defineEmits<{
  highlight: [diff: ChainDiffSummary | null]
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

const diff = computed(() => computeChainDiff(props.currentChainJson, props.chainJson))
const hasDiff = computed(() => checkHasDiff(diff.value))

watch(diff, (value) => {
  emit('highlight', value)
}, { immediate: true })
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

.ai-proposal-diff {
  margin-bottom: 8px;
  padding: 8px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.diff-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #303133;
}

.diff-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}

.ai-proposal-json :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
