<template>
  <div class="settings-ai-rag">
    <el-alert type="info" :closable="false" show-icon class="rag-hint">
      <template #title>{{ $t('settings.ai.rag.hintTitle') }}</template>
      <template #default>
        <p>{{ $t('settings.ai.rag.hintBody') }}</p>
        <p class="rag-path">{{ $t('settings.ai.rag.filesystemHint', { path: filesystemPath }) }}</p>
      </template>
    </el-alert>

    <el-card v-if="executorStatus && !executorStatus.error" class="executor-rag-card" shadow="never">
      <template #header>
        <span>{{ $t('settings.ai.rag.executorTitle') }}</span>
        <el-tag size="small" type="success" style="margin-left:8px">{{ $t('settings.ai.rag.executorPrimary') }}</el-tag>
      </template>
      <el-descriptions :column="2" size="small" border>
        <el-descriptions-item :label="$t('settings.ai.rag.executorPatterns')">
          {{ executorStatus.patternCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('settings.ai.rag.executorEvents')">
          {{ executorStatus.eventCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('settings.ai.rag.executorStorage')" :span="2">
          <span class="rag-path">{{ executorStatus.storage || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-alert
      v-else-if="filterAppCode && executorStatus?.error"
      type="warning"
      :closable="false"
      show-icon
      class="rag-hint"
      :title="$t('settings.ai.rag.executorUnavailable')"
    />

    <div class="rag-toolbar">
      <el-select v-model="filterAppCode" clearable filterable :placeholder="$t('design.selectApp')" class="rag-filter">
        <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
      </el-select>
      <el-button @click="loadDocuments">{{ $t('design.search') }}</el-button>
      <el-button type="primary" @click="openCreate">{{ $t('settings.ai.rag.create') }}</el-button>
      <el-button @click="rebuildIndex" :loading="rebuilding">{{ $t('settings.ai.rag.rebuildIndex') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="documents" row-key="id" size="small" class="rag-table">
      <el-table-column prop="title" :label="$t('settings.ai.rag.titleCol')" min-width="160" />
      <el-table-column prop="appCode" :label="$t('design.selectApp')" width="120">
        <template #default="{ row }">{{ row.appCode || $t('settings.ai.rag.globalScope') }}</template>
      </el-table-column>
      <el-table-column prop="enabled" :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? $t('settings.ai.rag.enabled') : $t('settings.ai.rag.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && documents.length === 0" :description="$t('settings.ai.rag.empty')" />

    <el-dialog v-model="editorVisible" :title="editorTitle" width="760px" destroy-on-close>
      <el-form :model="editorForm" label-width="100px">
        <el-form-item :label="$t('settings.ai.rag.titleCol')" required>
          <el-input v-model="editorForm.title" />
        </el-form-item>
        <el-form-item :label="$t('design.selectApp')">
          <el-select v-model="editorForm.appCode" clearable filterable class="rag-filter">
            <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="editorForm.enabled" />
        </el-form-item>
        <el-form-item :label="$t('settings.ai.rag.content')" required>
          <el-input v-model="editorForm.content" type="textarea" :rows="16" class="rag-editor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveDocument">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aiApi, type AiRagDocument, type AiRagDocumentSaveDTO } from '@/api/ai'
import { executorApi, type AppOption } from '@/api/executor'

const { t } = useI18n()

const apps = ref<AppOption[]>([])
const documents = ref<AiRagDocument[]>([])
const loading = ref(false)
const saving = ref(false)
const rebuilding = ref(false)
const filterAppCode = ref('')
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const filesystemPath = ref('./data/ai-rag/{tenantId}/*.md')
const executorStatus = ref<import('@/api/ai').ExecutorRagStatus | null>(null)

const editorForm = reactive<AiRagDocumentSaveDTO>({
  title: '',
  appCode: '',
  content: '',
  enabled: true,
})

const editorTitle = computed(() =>
  editingId.value ? t('settings.ai.rag.edit') : t('settings.ai.rag.create'),
)

function formatTime(v?: string) {
  if (!v) return '-'
  return v.replace('T', ' ')
}

async function loadApps() {
  try {
    apps.value = await executorApi.listApps()
  } catch {
    apps.value = []
  }
}

async function loadDocuments() {
  loading.value = true
  try {
    documents.value = await aiApi.listRagDocuments(filterAppCode.value || undefined)
    await loadStatus()
  } catch {
    documents.value = []
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    const status = await aiApi.getRagStatus(filterAppCode.value || undefined)
    if (status?.filesystemPath) {
      filesystemPath.value = status.filesystemPath
    }
    executorStatus.value = status?.executor ?? null
  } catch { /* ignore */ }
}

function openCreate() {
  editingId.value = null
  editorForm.title = ''
  editorForm.appCode = filterAppCode.value || ''
  editorForm.content = '## 标题\n\n在此编写 Markdown 知识片段，建议按 `##` 分节。'
  editorForm.enabled = true
  editorVisible.value = true
}

function openEdit(row: AiRagDocument) {
  editingId.value = row.id
  editorForm.title = row.title
  editorForm.appCode = row.appCode || ''
  editorForm.content = row.content
  editorForm.enabled = row.enabled !== false
  editorVisible.value = true
}

async function saveDocument() {
  if (!editorForm.title.trim() || !editorForm.content.trim()) {
    ElMessage.warning(t('settings.ai.rag.requiredFields'))
    return
  }
  saving.value = true
  try {
    const payload = {
      ...editorForm,
      appCode: editorForm.appCode || undefined,
    }
    if (editingId.value) {
      await aiApi.updateRagDocument(editingId.value, payload)
    } else {
      await aiApi.saveRagDocument(payload)
    }
    ElMessage.success(t('common.updateSuccess'))
    editorVisible.value = false
    await loadDocuments()
  } catch {
    ElMessage.error(t('settings.ai.rag.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: AiRagDocument) {
  try {
    await ElMessageBox.confirm(
      t('settings.ai.rag.deleteConfirm', { name: row.title }),
      t('common.confirm'),
      { type: 'warning' },
    )
    await aiApi.deleteRagDocument(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadDocuments()
  } catch { /* cancelled */ }
}

async function rebuildIndex() {
  rebuilding.value = true
  try {
    await aiApi.rebuildRagIndex()
    ElMessage.success(t('settings.ai.rag.rebuildSuccess'))
  } catch {
    ElMessage.error(t('settings.ai.rag.rebuildFailed'))
  } finally {
    rebuilding.value = false
  }
}

onMounted(async () => {
  await loadApps()
  await loadStatus()
  await loadDocuments()
})
</script>

<style scoped>
.executor-rag-card {
  margin-bottom: 12px;
}

.settings-ai-rag .rag-hint {
  margin-bottom: 16px;
}
.rag-hint p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
}
.rag-path {
  font-family: ui-monospace, monospace;
  color: #606266;
}
.rag-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.rag-filter {
  width: 220px;
}
.rag-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
