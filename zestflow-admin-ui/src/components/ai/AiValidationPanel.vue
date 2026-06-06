<template>
  <div v-if="validation" class="ai-validation-panel">
    <div class="ai-validation-header">
      <el-tag :type="validation.valid ? 'success' : 'danger'" size="small">
        {{ validation.valid ? $t('ai.validationPass') : $t('ai.validationFail') }}
      </el-tag>
      <span class="ai-validation-count">
        {{ $t('ai.errorCount', { count: validation.errors?.length || 0 }) }}
      </span>
    </div>
    <ul v-if="validation.errors?.length" class="ai-validation-errors">
      <li v-for="(err, i) in validation.errors" :key="i">{{ err }}</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type { AiValidationResult } from '@/api/ai'

defineProps<{
  validation: AiValidationResult | null
}>()
</script>

<style scoped>
.ai-validation-panel {
  border-top: 1px solid #ebeef5;
  padding: 12px;
}

.ai-validation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-validation-count {
  font-size: 12px;
  color: #909399;
}

.ai-validation-errors {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #f56c6c;
  line-height: 1.6;
}
</style>
