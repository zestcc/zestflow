<template>
  <div class="settings-ai-page">
    <div class="page-header">
      <h3 class="page-title">{{ $t('settings.ai.title') }}</h3>
      <p class="page-desc">{{ $t('settings.ai.description') }}</p>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="free-tier-alert"
      :title="$t('settings.ai.freeTierHint')"
    >
      <template #default>
        <p class="free-tier-desc">{{ $t('settings.ai.envKeyHint') }}</p>
      </template>
    </el-alert>

    <el-card v-loading="loading" shadow="never">
      <el-form ref="formRef" :model="form" label-width="140px" class="ai-form">
        <el-form-item :label="$t('settings.ai.enabled')">
          <el-switch v-model="form.enabled" />
        </el-form-item>

        <el-form-item :label="$t('settings.ai.provider')" prop="preset">
          <el-select
            v-model="form.preset"
            filterable
            class="ai-form-control"
            @change="onPresetChange"
          >
            <el-option-group :label="$t('settings.ai.tierA')">
              <el-option
                v-for="p in tierAPresets"
                :key="p.id"
                :label="presetLabel(p)"
                :value="p.id"
              />
            </el-option-group>
            <el-option-group :label="$t('settings.ai.tierB')">
              <el-option
                v-for="p in tierBPresets"
                :key="p.id"
                :label="presetLabel(p)"
                :value="p.id"
              />
            </el-option-group>
          </el-select>
        </el-form-item>

        <el-form-item v-if="selectedPreset?.id === 'custom'" :label="$t('settings.ai.baseUrl')">
          <el-input v-model="form.baseUrl" :placeholder="$t('settings.ai.baseUrlPlaceholder')" />
        </el-form-item>

        <el-form-item :label="$t('settings.ai.model')">
          <el-select v-model="form.model" filterable allow-create class="ai-form-control">
            <el-option
              v-for="m in modelOptions"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="$t('settings.ai.apiKey')">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="apiKeyPlaceholder"
          />
          <div v-if="tenantConfig?.apiKeyMasked" class="field-hint">
            {{ $t('settings.ai.apiKeyMasked', { masked: tenantConfig.apiKeyMasked }) }}
          </div>
          <a
            v-if="selectedPreset?.docUrl"
            :href="selectedPreset.docUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="doc-link"
          >
            {{ $t('settings.ai.howToGetKey') }}
          </a>
        </el-form-item>

        <el-form-item v-if="selectedPreset?.tags?.length" :label="$t('settings.ai.tags')">
          <el-tag v-for="tag in selectedPreset.tags" :key="tag" size="small" style="margin-right:6px">
            {{ tag }}
          </el-tag>
        </el-form-item>

        <el-form-item v-if="selectedPreset?.notes" :label="$t('settings.ai.notes')">
          <span class="preset-notes">{{ localizedNotes(selectedPreset) }}</span>
        </el-form-item>

        <el-form-item>
          <el-button :loading="testing" @click="handleTest">{{ $t('settings.ai.testConnection') }}</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="testResult"
        :title="testResult.success ? $t('settings.ai.testSuccess') : $t('settings.ai.testFailed')"
        :type="testResult.success ? 'success' : 'error'"
        :description="testResultMessage"
        show-icon
        :closable="true"
        style="margin-top:12px"
        @close="testResult = null"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  aiApi,
  type AiProviderPreset,
  type AiTenantConfigVO,
  type AiTestResponse,
} from '@/api/ai'

const { t, locale } = useI18n()

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const presets = ref<AiProviderPreset[]>([])
const tenantConfig = ref<AiTenantConfigVO | null>(null)
const testResult = ref<AiTestResponse | null>(null)

const form = reactive({
  enabled: false,
  preset: 'deepseek',
  baseUrl: '',
  apiKey: '',
  model: '',
})

const tierAPresets = computed(() => presets.value.filter(p => p.tier === 'A'))
const tierBPresets = computed(() => presets.value.filter(p => p.tier === 'B'))

const selectedPreset = computed(() => presets.value.find(p => p.id === form.preset))

const modelOptions = computed(() => {
  const p = selectedPreset.value
  if (!p) return []
  const models = [...(p.models || [])]
  if (p.defaultModel && !models.includes(p.defaultModel)) {
    models.unshift(p.defaultModel)
  }
  return models
})

const apiKeyPlaceholder = computed(() => {
  if (selectedPreset.value?.apiKeyRequired === false) {
    return selectedPreset.value.apiKeyPlaceholder || t('settings.ai.apiKeyOptional')
  }
  if (tenantConfig.value?.hasApiKey) {
    return t('settings.ai.apiKeyKeep')
  }
  return t('settings.ai.apiKeyPlaceholder')
})

const testResultMessage = computed(() => {
  if (!testResult.value) return ''
  const parts = [testResult.value.message || '']
  if (testResult.value.latencyMs != null) {
    parts.push(t('settings.ai.latency', { ms: testResult.value.latencyMs }))
  }
  if (testResult.value.model) {
    parts.push(t('settings.ai.testModel', { model: testResult.value.model }))
  }
  return parts.filter(Boolean).join(' · ')
})

function presetLabel(p: AiProviderPreset) {
  return locale.value === 'en' ? (p.displayNameEn || p.displayName) : p.displayName
}

function localizedNotes(p: AiProviderPreset) {
  return p.notes || ''
}

function onPresetChange() {
  const p = selectedPreset.value
  if (p) {
    form.model = p.defaultModel || ''
    if (p.id !== 'custom') {
      form.baseUrl = p.baseUrl || ''
    }
  }
  testResult.value = null
}

async function loadData() {
  loading.value = true
  try {
    const [providerRes, configRes] = await Promise.all([
      aiApi.getProviders(),
      aiApi.getTenantConfig(),
    ])
    presets.value = providerRes?.presets || []
    tenantConfig.value = configRes
    form.enabled = !!configRes.enabled
    form.preset = configRes.preset || 'deepseek'
    form.baseUrl = configRes.baseUrl || ''
    form.model = configRes.model || selectedPreset.value?.defaultModel || ''
    form.apiKey = ''
  } catch {
    ElMessage.error(t('settings.ai.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      enabled: form.enabled,
      preset: form.preset,
      model: form.model,
    }
    if (form.preset === 'custom' && form.baseUrl) {
      payload.baseUrl = form.baseUrl
    }
    if (form.apiKey.trim()) {
      payload.apiKey = form.apiKey.trim()
    }
    tenantConfig.value = await aiApi.saveTenantConfig(payload as any)
    form.apiKey = ''
    ElMessage.success(t('settings.ai.saveSuccess'))
  } catch {
    ElMessage.error(t('settings.ai.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await aiApi.testConnection({
      preset: form.preset,
      baseUrl: form.preset === 'custom' ? form.baseUrl : undefined,
      apiKey: form.apiKey.trim() || undefined,
      model: form.model,
    })
  } catch (e: any) {
    testResult.value = {
      success: false,
      message: e?.message || t('settings.ai.testFailed'),
    }
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.settings-ai-page {
  padding: 0 4px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.free-tier-alert {
  margin-bottom: 16px;
}

.free-tier-desc {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
}

.ai-form {
  max-width: 640px;
}

.ai-form-control {
  width: 100%;
}

.field-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.doc-link {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  text-decoration: none;
}

.doc-link:hover {
  text-decoration: underline;
}

.preset-notes {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}
</style>
