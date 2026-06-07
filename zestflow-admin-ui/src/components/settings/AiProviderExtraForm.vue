<template>
  <div class="ai-provider-extra-form">
    <el-divider content-position="left">{{ $t('dict.aiProvider.sectionBasic') }}</el-divider>
    <el-form-item :label="$t('dict.aiProvider.tier')">
      <el-select v-model="form.tier" style="width:100%" @change="onTierChange">
        <el-option v-for="opt in tierOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.region')">
      <el-select v-model="form.region" style="width:100%">
        <el-option v-for="opt in regionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.displayNameEn')">
      <el-input v-model="form.displayNameEn" maxlength="128" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.qualityTier')">
      <el-select v-model="form.qualityTier" clearable style="width:100%">
        <el-option v-for="opt in qualityOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </el-form-item>

    <el-divider content-position="left">{{ $t('dict.aiProvider.sectionConnection') }}</el-divider>
    <el-form-item :label="$t('settings.ai.baseUrl')">
      <el-input v-model="form.baseUrl" maxlength="512" :placeholder="$t('settings.ai.baseUrlPlaceholder')" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.defaultModel')">
      <el-input v-model="form.defaultModel" maxlength="128" :placeholder="$t('dict.aiProvider.defaultModelHint')" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.apiKeyRequired')">
      <el-switch v-model="form.apiKeyRequired" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.apiKeyPlaceholder')">
      <el-input v-model="form.apiKeyPlaceholder" maxlength="128" :placeholder="$t('settings.ai.apiKeyOptional')" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.docUrl')">
      <el-input v-model="form.docUrl" maxlength="512" placeholder="https://" />
    </el-form-item>

    <el-divider content-position="left">{{ $t('dict.aiProvider.sectionTags') }}</el-divider>
    <el-form-item :label="$t('settings.ai.tags')">
      <el-select v-model="form.tags" multiple filterable clearable style="width:100%">
        <el-option v-for="opt in tagOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.recommendedFor')">
      <el-select v-model="form.recommendedFor" multiple filterable clearable style="width:100%">
        <el-option v-for="opt in recommendedOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </el-form-item>
    <el-form-item :label="$t('settings.ai.notes')">
      <el-input v-model="form.notes" type="textarea" :rows="2" maxlength="512" />
    </el-form-item>

    <el-divider content-position="left">{{ $t('dict.aiProvider.sectionLifecycle') }}</el-divider>
    <el-form-item :label="$t('dict.aiProvider.deprecated')">
      <el-switch v-model="form.deprecated" />
    </el-form-item>
    <el-form-item :label="$t('dict.aiProvider.successor')">
      <el-input v-model="form.successor" maxlength="64" :placeholder="$t('dict.aiProvider.successorHint')" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { useDict } from '@/composables/useDict'
import {
  emptyAiProviderExtra,
  parseAiProviderExtra,
  serializeAiProviderExtra,
  tagTypeForAiProviderTier,
  type AiProviderExtra,
} from '@/utils/aiProviderExtra'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'tier-change': [tier: string, tagType: string]
}>()

const { options: tierOptions } = useDict('ai_provider_tier')
const { options: regionOptions } = useDict('ai_provider_region')
const { options: qualityOptions } = useDict('ai_quality_tier')
const { options: tagOptions } = useDict('ai_provider_tag')
const { options: recommendedOptions } = useDict('ai_recommended_for')

const form = reactive<AiProviderExtra>(emptyAiProviderExtra())

let syncingFromProp = false

function applyFromProp(raw: string) {
  syncingFromProp = true
  Object.assign(form, parseAiProviderExtra(raw))
  syncingFromProp = false
}

function emitSerialized() {
  if (syncingFromProp) return
  const next = serializeAiProviderExtra(form)
  if (next !== props.modelValue) {
    emit('update:modelValue', next)
  }
}

function onTierChange(tier: string) {
  emit('tier-change', tier, tagTypeForAiProviderTier(tier))
  emitSerialized()
}

watch(
  () => props.modelValue,
  (v) => {
    if (v === serializeAiProviderExtra(form)) return
    applyFromProp(v)
  },
  { immediate: true },
)

watch(
  form,
  () => emitSerialized(),
  { deep: true },
)
</script>

<style scoped>
.ai-provider-extra-form :deep(.el-divider) {
  margin: 8px 0 16px;
}
</style>
