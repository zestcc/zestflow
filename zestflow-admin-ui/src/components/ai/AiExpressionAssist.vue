<template>
  <div class="ai-expression-assist">
    <el-button
      size="small"
      type="primary"
      link
      :disabled="disabled || loading"
      @click="openDialog"
    >
      {{ $t('ai.expressionAssist') }}
    </el-button>

    <el-dialog
      v-model="visible"
      :title="$t('ai.expressionAssistTitle')"
      width="520px"
      append-to-body
      destroy-on-close
      @closed="resetDialog"
    >
      <el-input
        v-model="prompt"
        type="textarea"
        :rows="3"
        :placeholder="$t('ai.expressionAssistPlaceholder')"
      />
      <div v-if="result" class="ai-expression-result">
        <div class="result-label">{{ $t('ai.expressionResult') }}</div>
        <pre class="result-code">{{ result.expression }}</pre>
        <p v-if="result.explanation" class="result-explain">{{ result.explanation }}</p>
      </div>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="loading" :disabled="!prompt.trim()" @click="handleSuggest">
          {{ $t('ai.generate') }}
        </el-button>
        <el-button type="success" :disabled="!result?.expression" @click="handleApply">
          {{ $t('ai.applyExpression') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { aiApi, type AiExpressionSuggestResponse } from '@/api/ai'
import type { AiCopilotContext } from '@/stores/aiCopilot'

const props = defineProps<{
  modelValue: string
  disabled?: boolean
  getContext: () => AiCopilotContext | null
  fieldLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const { t } = useI18n()
const visible = ref(false)
const prompt = ref('')
const loading = ref(false)
const result = ref<AiExpressionSuggestResponse | null>(null)

function openDialog() {
  visible.value = true
  if (!prompt.value.trim() && props.fieldLabel) {
    prompt.value = t('ai.expressionAssistDefaultPrompt', { field: props.fieldLabel })
  }
}

function resetDialog() {
  prompt.value = ''
  result.value = null
  loading.value = false
}

async function handleSuggest() {
  const ctx = props.getContext()
  if (!ctx || !prompt.value.trim()) return
  loading.value = true
  try {
    result.value = await aiApi.suggestExpression({
      appCode: ctx.appCode,
      designId: ctx.designId,
      chainCode: ctx.chainCode,
      currentExpression: props.modelValue,
      userMessage: prompt.value.trim(),
      context: ctx.currentChainData,
    })
  } catch {
    ElMessage.error(t('ai.expressionAssistFailed'))
  } finally {
    loading.value = false
  }
}

function handleApply() {
  if (!result.value?.expression) return
  emit('update:modelValue', result.value.expression)
  visible.value = false
  ElMessage.success(t('ai.applyExpressionSuccess'))
}
</script>

<style scoped>
.ai-expression-assist {
  display: inline-flex;
  margin-left: 4px;
}
.ai-expression-result {
  margin-top: 12px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}
.result-label {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
}
.result-code {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, monospace;
}
.result-explain {
  margin: 8px 0 0;
  font-size: 12px;
  color: #606266;
}
</style>
