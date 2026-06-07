<template>
  <div class="sys-config-page">
    <div class="page-header">
      <div class="page-header-row">
        <div class="page-stats-row">
          <span class="summary-total">{{ $t('sysConfig.total') }} {{ total }}</span>
        </div>
        <el-button type="primary" @click="openCreate">{{ $t('sysConfig.create') }}</el-button>
      </div>
      <el-alert type="info" :closable="false" show-icon class="platform-hint">
        {{ $t('sysConfig.platformHint') }}
      </el-alert>
    </div>

    <el-form :model="filter" inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
      <el-form-item :label="$t('common.keyword')">
        <el-input v-model="filter.keyword" :placeholder="$t('sysConfig.keywordPlaceholder')" clearable class="page-filter-control" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('sysConfig.category')">
        <el-select v-model="filter.category" :placeholder="$t('common.all')" clearable filterable allow-create class="page-filter-control--sm">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <DictSelect v-model="filter.status" type-code="enable_status" class="page-filter-control--sm" />
      </el-form-item>
      <el-form-item class="filter-actions-item">
        <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <ResponsiveTable
      :data="list"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="220"
    >
      <template #configValue="{ row }">
        <span class="config-value-preview">{{ previewValue(row) }}</span>
      </template>
      <template #valueType="{ row }">
        <el-tag size="small" type="info">{{ row.valueType }}</el-tag>
      </template>
      <template #category="{ row }">
        <el-tag size="small">{{ row.category }}</el-tag>
      </template>
      <template #status="{ row }">
        <el-tag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </el-tag>
      </template>
      <template #actions="{ row }">
        <el-button text size="small" type="primary" class="action-btn" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" class="action-btn" @click="toggleStatus(row)">
          {{ row.status === 1 ? $t('dict.disable') : $t('dict.enable') }}
        </el-button>
        <el-button text size="small" type="danger" class="action-btn" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
      </template>
    </ResponsiveTable>

    <div class="page-pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="paginationLayout"
        @current-change="fetchList"
        @size-change="page=1;fetchList()"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('sysConfig.edit') : $t('sysConfig.create')"
      :width="640"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="$t('sysConfig.configKey')" prop="configKey">
          <el-input v-model="form.configKey" :disabled="isEditing" maxlength="128" :placeholder="$t('sysConfig.configKeyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('sysConfig.configName')" prop="configName">
          <el-input v-model="form.configName" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('sysConfig.valueType')" prop="valueType">
          <DictSelect v-model="form.valueType" type-code="config_value_type" :clearable="false" style="width:180px" />
        </el-form-item>
        <el-form-item :label="$t('sysConfig.category')">
          <el-select v-model="form.category" filterable allow-create default-first-option style="width:220px">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sysConfig.configValue')" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="form.valueType === 'json' ? 8 : 4" :placeholder="valuePlaceholder" />
        </el-form-item>
        <el-form-item :label="$t('dict.sort')">
          <el-input v-model="form.sort" type="number" min="0" style="width:180px" :placeholder="$t('dict.sortPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="$t('dict.remark')">
          <el-input v-model="form.remark" type="textarea" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { sysConfigApi, type SysConfigVO } from '@/api/sysConfig'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import DictSelect from '@/components/common/DictSelect.vue'
import { useDictLabel } from '@/composables/useDictLabel'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const { paginationLayout } = useResponsivePagination()
const { labelOf: statusLabel, tagTypeOf: statusTagType } = useDictLabel('enable_status')

const columns = computed(() => [
  { prop: 'configKey', label: t('sysConfig.configKey'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'configName', label: t('sysConfig.configName'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'configValue', label: t('sysConfig.configValue'), minWidth: 180, showOverflowTooltip: true },
  { prop: 'valueType', label: t('sysConfig.valueType'), width: 90, align: 'center' as const },
  { prop: 'category', label: t('sysConfig.category'), width: 110, align: 'center' as const },
  { prop: 'sort', label: t('dict.sort'), width: 70, align: 'center' as const },
  { prop: 'status', label: t('common.status'), width: 80, align: 'center' as const },
])

const loading = ref(false)
const list = ref<SysConfigVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const categories = ref<string[]>([])
const filter = reactive({ keyword: '', category: '', status: '' as number | string })

const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<any>(null)
const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
  valueType: 'json',
  category: 'system',
  sort: null as number | null,
  status: 1,
  remark: '',
})

const valuePlaceholder = computed(() => {
  if (form.valueType === 'json') return t('sysConfig.jsonPlaceholder')
  if (form.valueType === 'bool') return 'true / false'
  if (form.valueType === 'number') return '123'
  return t('sysConfig.textPlaceholder')
})

const rules = {
  configKey: [{ required: true, message: () => t('validation.required', { field: t('sysConfig.configKey') }), trigger: 'blur' }],
  configName: [{ required: true, message: () => t('validation.required', { field: t('sysConfig.configName') }), trigger: 'blur' }],
  valueType: [{ required: true, message: () => t('validation.required', { field: t('sysConfig.valueType') }), trigger: 'change' }],
  configValue: [{
    validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
      if (form.valueType !== 'json' || !value?.trim()) {
        callback()
        return
      }
      try {
        JSON.parse(value)
        callback()
      } catch {
        callback(new Error(t('sysConfig.invalidJson')))
      }
    },
    trigger: 'blur',
  }],
}

function previewValue(row: SysConfigVO) {
  const v = row.configValue || ''
  if (v.length <= 80) return v || '-'
  return v.slice(0, 80) + '…'
}

async function fetchCategories() {
  categories.value = await sysConfigApi.categories()
}

async function fetchList() {
  loading.value = true
  try {
    const res = await sysConfigApi.list({
      keyword: filter.keyword || undefined,
      category: filter.category || undefined,
      status: filter.status === '' ? undefined : (filter.status as number),
      page: page.value,
      size: pageSize.value,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { page.value = 1; fetchList() }
function handleReset() {
  filter.keyword = ''
  filter.category = ''
  filter.status = ''
  page.value = 1
  fetchList()
}

function resetForm() {
  form.configKey = ''
  form.configName = ''
  form.configValue = ''
  form.valueType = 'json'
  form.category = 'system'
  form.sort = null
  form.status = 1
  form.remark = ''
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: SysConfigVO) {
  isEditing.value = true
  editingId.value = row.id
  form.configKey = row.configKey
  form.configName = row.configName
  form.configValue = row.configValue || ''
  form.valueType = row.valueType || 'json'
  form.category = row.category || 'system'
  form.sort = row.sort
  form.status = row.status
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      configName: form.configName,
      configValue: form.configValue || undefined,
      valueType: form.valueType,
      category: form.category || 'system',
      sort: form.sort ?? undefined,
      status: form.status,
      remark: form.remark || undefined,
    }
    if (isEditing.value && editingId.value) {
      await sysConfigApi.update(editingId.value, payload)
    } else {
      await sysConfigApi.create({
        configKey: form.configKey,
        ...payload,
      })
    }
    dialogVisible.value = false
    ElMessage.success(t('common.save'))
    fetchCategories()
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: SysConfigVO) {
  await sysConfigApi.toggleStatus(row.id)
  ElMessage.success(t('common.save'))
  fetchList()
}

async function handleDelete(row: SysConfigVO) {
  await ElMessageBox.confirm(t('sysConfig.deleteConfirm', { key: row.configKey }), t('common.confirm'), { type: 'warning' })
  await sysConfigApi.delete(row.id)
  ElMessage.success(t('common.save'))
  fetchList()
}

onMounted(async () => {
  await fetchCategories()
  await fetchList()
})
</script>

<style scoped>
.summary-total { font-weight: 600; color: #409eff; }
.platform-hint { margin-top: 12px; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
.config-value-preview {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #606266;
}
</style>
