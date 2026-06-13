<template>
  <div class="chain-detail-page">
    <div class="page-toolbar">
      <el-button @click="router.push('/chains')">{{ $t('chains.backToList') }}</el-button>
      <div class="toolbar-actions">
        <el-button v-if="detail && detail.status === 2 && detail.designCode" type="primary" :loading="publishing" @click="handlePublish">
          {{ $t('chains.publish') }}
        </el-button>
        <el-button v-if="detail?.designCode" type="primary" plain @click="openDesignEditor">
          {{ $t('chains.design') }}
        </el-button>
        <el-button v-if="detail" @click="openEdit">{{ $t('common.edit') }}</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <template v-if="detail">
        <div class="detail-title">{{ detail.name }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('chains.code')">
            <el-tag size="small" style="font-family:monospace">{{ detail.code }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.chainKey')">
            <span v-if="detail.chainKey" class="mono">{{ detail.chainKey }}</span>
            <span v-else class="muted">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.appDeclared')">
            <el-tag v-if="detail.appDeclared" type="warning" size="small">{{ $t('chains.appDeclaredTag') }}</el-tag>
            <span v-else class="muted">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.status')">
            <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.app')">{{ detail.appCode || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.publishStatus')">
            {{ detail.publishedCount || 0 }}/{{ detail.totalExecutors || 0 }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.description')">{{ detail.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.designName')">
            <span v-if="detail.designCode" class="code-link" @click="openDesignDetail">{{ detail.designCode }}</span>
            <span v-else class="muted">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.createdAt')">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedBy')">{{ detail.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.updatedAt')">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <el-empty v-else-if="!loading" :description="$t('common.requestFailed')" />
    </el-card>

    <el-dialog v-model="editVisible" :title="$t('chains.editChain')" width="500px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px" @submit.prevent>
        <el-form-item :label="$t('chains.name')" prop="name">
          <el-input v-model="editForm.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.description')" prop="description">
          <el-input v-model="editForm.description" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <DesignDetailDrawer ref="designDetailDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { chainApi, type ChainVO } from '@/api/chain'
import DesignDetailDrawer from '@/components/DesignDetailDrawer.vue'
import { useDictLabel } from '@/composables/useDictLabel'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { labelOf: chainStatusLabel, tagTypeOf: chainStatusTagType } = useDictLabel('chain_lifecycle_status')

const loading = ref(false)
const detail = ref<ChainVO | null>(null)
const publishing = ref(false)
const editVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<any>(null)
const editForm = ref({ name: '', description: '' })
const editRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
}
const designDetailDrawerRef = ref<InstanceType<typeof DesignDetailDrawer> | null>(null)

function chainCode() {
  return String(route.params.id || '')
}

function appCode() {
  return String(route.query.appCode || '')
}

function statusTagType(status: number): string {
  return chainStatusTagType(status)
}

function statusLabel(status: number): string {
  return chainStatusLabel(status)
}

function formatTime(v?: string) {
  return v ? v.replace('T', ' ') : '-'
}

async function loadDetail() {
  const code = chainCode()
  const app = appCode()
  if (!code || !app) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await chainApi.getByCode(code, app)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function openDesignDetail() {
  if (detail.value?.designCode && detail.value.appCode) {
    designDetailDrawerRef.value?.open(detail.value.designCode, detail.value.appCode)
  }
}

function openDesignEditor() {
  if (detail.value?.designCode) {
    router.push({ name: 'DesignEditor', params: { id: detail.value.designCode }, query: { appCode: detail.value.appCode } })
  }
}

function openEdit() {
  if (!detail.value) return
  editForm.value = { name: detail.value.name, description: detail.value.description || '' }
  editVisible.value = true
}

async function handleEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !detail.value?.appCode) return
  editSubmitting.value = true
  try {
    await chainApi.update(detail.value.code, {
      name: editForm.value.name,
      description: editForm.value.description || undefined,
    })
    ElMessage.success(t('chains.saveSuccess'))
    editVisible.value = false
    await loadDetail()
  } finally {
    editSubmitting.value = false
  }
}

async function handlePublish() {
  if (!detail.value?.appCode) return
  if ((detail.value as any).deliveryLifecycle === 'bootstrap') {
    ElMessage.error(t('chains.bootstrapPublishBlocked'))
    return
  }
  try {
    await ElMessageBox.confirm(t('chains.publishConfirm'), { type: 'warning' })
  } catch {
    return
  }
  publishing.value = true
  try {
    const res = await chainApi.publish(detail.value.code, detail.value.appCode)
    if (res.code === 200) {
      ElMessage.success(t('chains.publishSuccess'))
    } else {
      ElMessage.warning(res.message || t('chains.publishFailed'))
    }
    await loadDetail()
  } finally {
    publishing.value = false
  }
}

onMounted(loadDetail)
watch(() => [route.params.id, route.query.appCode], loadDetail)
</script>

<style scoped>
.chain-detail-page {
  background: #fff;
  padding: 16px 20px;
  border-radius: 4px;
}
.page-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.detail-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}
.mono {
  font-family: monospace;
}
.muted {
  color: #c0c4cc;
}
.code-link {
  color: var(--el-color-primary);
  cursor: pointer;
}
</style>
