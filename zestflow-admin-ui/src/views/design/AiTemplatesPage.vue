<template>
  <div class="ai-templates-page">
    <div class="page-header">
      <div class="page-header-row">
        <div>
          <h3 class="page-title">{{ $t('ai.templates.title') }}</h3>
          <p class="page-desc">{{ $t('ai.templates.description') }}</p>
        </div>
        <el-button type="primary" @click="openCreate">{{ $t('ai.templates.create') }}</el-button>
      </div>
      <div class="page-toolbar">
        <el-select
          v-model="currentAppCode"
          filterable
          class="page-filter-control"
          :placeholder="$t('design.selectApp')"
          @change="loadTemplates"
        >
          <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
        </el-select>
        <el-button type="primary" @click="loadTemplates">{{ $t('design.search') }}</el-button>
      </div>
    </div>

    <ResponsiveTable
      :data="templates"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="220"
    >
      <template #name="{ row }">
        <span class="code-link" @click="openDetail(row)">{{ row.name }}</span>
      </template>
      <template #appCode="{ row }">{{ row.appCode || '-' }}</template>
      <template #createdAt="{ row }">{{ formatTime(row.createdAt) }}</template>
      <template #actions="{ row }">
        <el-button text type="primary" size="small" class="action-btn" @click="openDetail(row)">
          {{ $t('common.detail') }}
        </el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="copyChainData(row)">
          {{ $t('ai.templates.copyJson') }}
        </el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="openApplyDialog(row)">
          {{ $t('ai.templates.applyCopilot') }}
        </el-button>
        <el-button text type="danger" size="small" class="action-btn" @click="handleDelete(row)">
          {{ $t('common.delete') }}
        </el-button>
      </template>
    </ResponsiveTable>

    <el-empty v-if="!loading && templates.length === 0" :description="$t('ai.templates.empty')" />

    <el-dialog v-model="detailVisible" :title="detail?.name" width="720px" destroy-on-close>
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item :label="$t('ai.templates.name')">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item :label="$t('ai.templates.descriptionLabel')">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('design.selectApp')">{{ detail.appCode || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('ai.templates.promptSummary')">{{ detail.promptSummary || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="margin:16px 0 8px">{{ $t('ai.templates.chainData') }}</h4>
      <pre class="json-block">{{ detail?.chainData }}</pre>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" @click="openApplyDialog(detail!)">{{ $t('ai.templates.applyCopilot') }}</el-button>
        <el-button type="primary" @click="goDesign">{{ $t('ai.templates.openDesign') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" :title="$t('ai.templates.create')" width="640px" destroy-on-close>
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="$t('ai.templates.name')" required>
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item :label="$t('ai.templates.descriptionLabel')">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="$t('design.selectApp')">
          <el-select v-model="createForm.appCode" filterable clearable class="page-filter-control">
            <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ai.templates.promptSummary')">
          <el-input v-model="createForm.promptSummary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="$t('ai.templates.chainData')" required>
          <el-input v-model="createForm.chainData" type="textarea" :rows="10" style="font-family:monospace;font-size:12px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="applyVisible" :title="$t('ai.templates.applyCopilot')" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="$t('design.name')" required>
          <el-input v-model="applyDesignCode" :placeholder="$t('ai.templates.designCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('design.selectApp')">
          <el-input :model-value="applyTarget?.appCode || currentAppCode" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!applyDesignCode.trim()" @click="confirmApplyToDesign">
          {{ $t('ai.templates.openInCopilot') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, type AiChainTemplate, type AiChainTemplateSaveDTO } from '@/api/ai'
import { executorApi, type AppOption } from '@/api/executor'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useCurrentApp } from '@/composables/useCurrentApp'

const { t } = useI18n()
const router = useRouter()
const { currentAppCode, syncFromApps } = useCurrentApp()

const apps = ref<AppOption[]>([])
const templates = ref<AiChainTemplate[]>([])
const loading = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const createVisible = ref(false)
const applyVisible = ref(false)
const applyDesignCode = ref('')
const applyTarget = ref<AiChainTemplate | null>(null)
const detail = ref<AiChainTemplate | null>(null)

const createForm = reactive<AiChainTemplateSaveDTO>({
  name: '',
  description: '',
  appCode: '',
  promptSummary: '',
  chainData: '{"nodes":[],"edges":[]}',
})

const columns = [
  { prop: 'name', label: t('ai.templates.name'), minWidth: 160 },
  { prop: 'description', label: t('ai.templates.descriptionLabel'), minWidth: 200 },
  { prop: 'appCode', label: t('design.selectApp'), width: 140 },
  { prop: 'createdBy', label: t('ai.templates.createdBy'), width: 120 },
  { prop: 'createdAt', label: t('ai.templates.createdAt'), width: 170 },
]

function formatTime(v?: string) {
  if (!v) return '-'
  return v.replace('T', ' ')
}

async function fetchApps() {
  try {
    apps.value = await executorApi.listApps()
    const code = syncFromApps(apps.value)
    if (code) currentAppCode.value = code
  } catch { /* ignore */ }
}

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await aiApi.listTemplates(currentAppCode.value || undefined)
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
}

function openDetail(row: AiChainTemplate) {
  detail.value = row
  detailVisible.value = true
}

function openCreate() {
  createForm.name = ''
  createForm.description = ''
  createForm.appCode = currentAppCode.value || ''
  createForm.promptSummary = ''
  createForm.chainData = '{"nodes":[],"edges":[]}'
  createVisible.value = true
}

async function handleCreate() {
  if (!createForm.name.trim() || !createForm.chainData.trim()) {
    ElMessage.warning(t('ai.templates.requiredFields'))
    return
  }
  saving.value = true
  try {
    await aiApi.saveTemplate({ ...createForm })
    ElMessage.success(t('ai.templates.saveSuccess'))
    createVisible.value = false
    await loadTemplates()
  } catch {
    ElMessage.error(t('ai.templates.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: AiChainTemplate) {
  try {
    await ElMessageBox.confirm(
      t('ai.templates.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await aiApi.deleteTemplate(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadTemplates()
  } catch { /* cancelled or failed */ }
}

async function copyChainData(row: AiChainTemplate) {
  try {
    await navigator.clipboard.writeText(row.chainData)
    ElMessage.success(t('ai.templates.copied'))
  } catch {
    ElMessage.error(t('ai.templates.copyFailed'))
  }
}

function goDesign() {
  if (!detail.value?.appCode) {
    router.push('/design')
    return
  }
  router.push({ path: '/design', query: { appCode: detail.value.appCode } })
}

function openApplyDialog(row: AiChainTemplate) {
  applyTarget.value = row
  applyDesignCode.value = ''
  applyVisible.value = true
}

function confirmApplyToDesign() {
  const code = applyDesignCode.value.trim()
  const row = applyTarget.value
  if (!code || !row) return
  applyVisible.value = false
  detailVisible.value = false
  router.push({
    name: 'DesignEditor',
    params: { id: code },
    query: {
      appCode: row.appCode || currentAppCode.value || undefined,
      aiTemplateId: String(row.id),
    },
  })
}

onMounted(async () => {
  await fetchApps()
  await loadTemplates()
})
</script>

<style scoped>
.ai-templates-page .page-header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.page-title {
  margin: 0 0 4px;
  font-size: 18px;
}
.page-desc {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.json-block {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
