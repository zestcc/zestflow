<template>
  <div class="settings-ai-page">
    <div class="page-header">
      <p class="page-desc">{{ $t('settings.ai.description') }}</p>
    </div>

    <el-tabs v-model="activeTab" class="settings-ai-tabs">
      <el-tab-pane :label="$t('settings.ai.tabConfig')" name="config">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="free-tier-alert"
          :title="$t('settings.ai.freeTierHint')"
        >
          <template #default>
            <p class="free-tier-desc">{{ $t('settings.ai.envKeyHint') }}</p>
            <p class="free-tier-desc">{{ $t('settings.ai.presetDocHint') }}</p>
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

        <el-form-item :label="$t('settings.ai.model')">
          <ComboboxInput
            v-model="form.model"
            :suggestions="modelSuggestions"
            :placeholder="$t('settings.ai.modelPlaceholder')"
          />
          <div class="field-hint">{{ $t('settings.ai.connectionFieldHint') }}</div>
        </el-form-item>

        <el-form-item :label="$t('settings.ai.baseUrl')">
          <ComboboxInput
            v-model="form.baseUrl"
            :suggestions="urlSuggestions"
            :placeholder="$t('settings.ai.baseUrlPlaceholder')"
          />
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
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.tabSystem')" name="system">
        <SettingsAiSystemPanel @providers-changed="reloadProviders" />
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.tabRag')" name="rag">
        <SettingsAiRagPanel />
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.tabUsage')" name="usage">
        <SettingsAiUsagePanel />
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.tabTrace')" name="trace">
        <SettingsAiTracePanel />
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.tabLearning')" name="learning">
        <SettingsAiLearningEventsPanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import SettingsAiRagPanel from '@/components/settings/SettingsAiRagPanel.vue'
import SettingsAiUsagePanel from '@/components/settings/SettingsAiUsagePanel.vue'
import SettingsAiTracePanel from '@/components/settings/SettingsAiTracePanel.vue'
import SettingsAiLearningEventsPanel from '@/components/settings/SettingsAiLearningEventsPanel.vue'
import SettingsAiSystemPanel from '@/components/settings/SettingsAiSystemPanel.vue'
import ComboboxInput from '@/components/common/ComboboxInput.vue'
import { useDictCascade } from '@/composables/useDictCascade'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  aiApi,
  type AiProviderPreset,
  type AiTenantConfigVO,
  type AiTestResponse,
} from '@/api/ai'

const { t, locale } = useI18n()

const activeTab = ref('config')
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

const { options: modelDictOptions } = useDictCascade(
  'ai_model',
  'ai_provider',
  computed(() => form.preset),
)

const modelSuggestions = computed(() => {
  const fromDict = modelDictOptions.value.map(m => m.value)
  const fromPreset = selectedPreset.value?.models ?? []
  const current = form.model?.trim()
  const set = new Set<string>([...fromDict, ...fromPreset])
  if (current) set.add(current)
  return [...set]
})

const urlSuggestions = computed(() => {
  const urls: string[] = []
  const p = selectedPreset.value
  if (p?.baseUrl) urls.push(p.baseUrl)
  const current = form.baseUrl?.trim()
  if (current && !urls.includes(current)) urls.unshift(current)
  return urls
})

function effectiveBaseUrl() {
  const trimmed = form.baseUrl?.trim()
  if (trimmed) return trimmed
  return selectedPreset.value?.baseUrl || ''
}

const apiKeyPlaceholder = computed(() => {
  if (selectedPreset.value?.apiKeyRequired === false) {
    return selectedPreset.value.apiKeyPlaceholder || t('settings.ai.apiKeyOptional')
  }
  if (tenantConfig.value?.apiKeyConfigured) {
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
    form.baseUrl = p.baseUrl || ''
  }
  testResult.value = null
}

function applyTenantConfig(config: AiTenantConfigVO) {
  tenantConfig.value = config
  form.enabled = !!config.enabled
  if (config.preset) {
    form.preset = config.preset
  }
  const preset = presets.value.find(p => p.id === form.preset)
  form.baseUrl = config.baseUrl ?? preset?.baseUrl ?? ''
  form.model = config.model ?? preset?.defaultModel ?? ''
}

async function reloadProviders() {
  try {
    const [providerRes, configRes] = await Promise.all([
      aiApi.getProviders(),
      aiApi.getTenantConfig(),
    ])
    presets.value = providerRes ?? []
    applyTenantConfig(configRes)
  } catch {
    // 静默：主加载流程会提示
  }
}

async function loadData() {
  loading.value = true
  try {
    const [providerRes, configRes] = await Promise.all([
      aiApi.getProviders(),
      aiApi.getTenantConfig(),
    ])
    presets.value = providerRes ?? []
    applyTenantConfig(configRes)
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
      model: form.model?.trim() || undefined,
      baseUrl: form.baseUrl?.trim() || undefined,
    }
    if (form.apiKey.trim()) {
      payload.apiKey = form.apiKey.trim()
    }
    tenantConfig.value = await aiApi.saveTenantConfig(payload as any)
    applyTenantConfig(tenantConfig.value)
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
      baseUrl: effectiveBaseUrl() || undefined,
      apiKey: form.apiKey.trim() || undefined,
      model: form.model?.trim() || '',
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

watch(activeTab, (tab) => {
  if (tab === 'config' || tab === 'system') {
    void reloadProviders()
  }
})
</script>

<style scoped>
.settings-ai-page {
  padding: 0 4px;
}

.page-header {
  margin-bottom: 16px;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.settings-ai-tabs {
  margin-top: 8px;
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
