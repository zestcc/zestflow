<template>
  <div class="dict-types-page">
    <div class="page-header">
      <div class="stats-summary">
        <span class="summary-total">{{ $t('dict.total') }} {{ list.length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-healthy">{{ $t('dict.enabled') }} {{ list.filter(d => d.status === 1).length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-offline">{{ $t('dict.disabled') }} {{ list.filter(d => d.status === 0).length }}</span>
      </div>
      <el-button type="primary" @click="showCreateType">{{ $t('dict.createType') }}</el-button>
    </div>

    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item :label="$t('common.keyword')">
        <el-input v-model="filter.keyword" :placeholder="$t('dict.filterPlaceholder')" clearable style="width:200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable style="width:100px">
          <el-option :label="$t('dict.enabled')" :value="1" />
          <el-option :label="$t('dict.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="list"
      v-loading="loading"
      stripe border
      style="width:100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="code" :label="$t('dict.code')" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="'never'" style="font-family:monospace;font-weight:500;cursor:pointer" @click="showDataDrawer(row)">
            {{ row.code }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="$t('dict.name')" show-overflow-tooltip min-width="120" />
      <el-table-column prop="description" :label="$t('dict.description')" show-overflow-tooltip min-width="140" />
      <el-table-column prop="sort" :label="$t('dict.sort')" width="70" align="center" />
      <el-table-column :label="$t('common.status')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('dict.enabled') : $t('dict.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedBy" :label="$t('common.updatedBy')" width="120" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="primary" class="action-btn" @click="showEditType(row)">{{ $t('common.edit') }}</el-button>
          <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" class="action-btn" @click="toggleTypeStatus(row)">
            {{ row.status === 1 ? $t('dict.disable') : $t('dict.enable') }}
          </el-button>
          <el-button text size="small" type="primary" class="action-btn" @click="showDataDrawer(row)">{{ $t('dict.dataItems') }}</el-button>
          <el-button text size="small" type="danger" class="action-btn" @click="handleDeleteType(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="page=1;fetchList()"
      />
    </div>

    <!-- 新建/编辑字典类型弹窗 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="isEditingType ? $t('dict.editType') : $t('dict.createType')"
      :width="600"
      :close-on-click-modal="false"
      @close="fetchList"
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="100px">
        <el-form-item :label="$t('dict.code')" prop="code" v-if="!isEditingType">
          <el-input v-model="typeForm.code" :placeholder="$t('dict.codePlaceholder')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('dict.name')" prop="name">
          <el-input v-model="typeForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('dict.description')">
          <el-input v-model="typeForm.description" type="textarea" maxlength="256" />
        </el-form-item>
        <el-form-item :label="$t('dict.sort')">
          <el-input v-model="typeForm.sort" type="number" min="0" style="width:180px" :placeholder="$t('dict.sortPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="saveType">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据项管理 Drawer -->
    <el-drawer
      v-model="dataDrawerVisible"
      :title="currentType?.code + ' - ' + currentType?.name"
      size="50%"
      destroy-on-close
      @close="fetchList"
    >
      <template #header>
        <div class="drawer-header">
          <span>{{ currentType?.code }} - {{ currentType?.name }}</span>
          <el-button type="primary" size="small" @click="showCreateData">{{ $t('dict.addData') }}</el-button>
        </div>
      </template>

      <el-table
        :data="dataList"
        v-loading="dataLoading"
        stripe border
        style="width:100%"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
      >
        <el-table-column prop="label" :label="$t('dict.label')" show-overflow-tooltip min-width="100" />
        <el-table-column prop="value" :label="$t('dict.value')" show-overflow-tooltip min-width="100" />
        <el-table-column prop="sort" :label="$t('dict.sort')" width="60" align="center" />
        <el-table-column :label="$t('common.status')" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? $t('dict.enabled') : $t('dict.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tagType" :label="$t('dict.tagType')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.tagType" :type="row.tagType" size="small">{{ row.tagType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('dict.default')" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.defaultFlag === 1" type="success" size="small">{{ $t('dict.yes') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" :label="$t('dict.remark')" show-overflow-tooltip min-width="100" />
        <el-table-column :label="$t('common.actions')" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" class="action-btn" @click="showEditData(row)">{{ $t('common.edit') }}</el-button>
            <el-button text size="small" type="danger" class="action-btn" @click="handleDeleteData(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 数据项抽屉内的底部详细区 -->
      <template v-if="selectedData" style="margin-top:16px">
        <el-descriptions :column="1" border size="small" style="margin-top:16px">
          <el-descriptions-item :label="$t('dict.createdBy')">{{ selectedData.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('dict.createdAt')">{{ selectedData.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('dict.updatedAt')">{{ selectedData.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 新建/编辑数据项弹窗 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="isEditingData ? $t('dict.editData') : $t('dict.addData')"
      :width="500"
      :close-on-click-modal="false"
      @close="refreshDataList"
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="100px">
        <el-form-item :label="$t('dict.label')" prop="label">
          <el-input v-model="dataForm.label" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('dict.value')" prop="value">
          <el-input v-model="dataForm.value" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('dict.sort')">
          <el-input v-model="dataForm.sort" type="number" min="0" style="width:180px" :placeholder="$t('dict.sortPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('dict.tagType')">
          <el-select v-model="dataForm.tagType" clearable style="width:200px">
            <el-option v-for="item in tagTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="dataForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="$t('dict.default')">
          <el-switch v-model="dataForm.defaultFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item :label="$t('dict.remark')">
          <el-input v-model="dataForm.remark" type="textarea" maxlength="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="dataSubmitting" @click="saveData">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { dictApi, type DictTypeVO, type DictDataVO } from '@/api/dict'
import { useDict } from '@/composables/useDict'
import { useDictStore } from '@/stores/dict'

const { t } = useI18n()
const dictStore = useDictStore()

const loading = ref(false)
const list = ref<DictTypeVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const filter = reactive({ keyword: '', status: '' as number | string })
const { options: tagTypeOptions } = useDict('tag_type')

const typeDialogVisible = ref(false)
const isEditingType = ref(false)
const editingTypeId = ref<number | null>(null)
const submitting = ref(false)
const typeFormRef = ref<any>(null)
const typeForm = reactive({ code: '', name: '', description: '', sort: null as number | null, status: 1 })
const typeRules = {
  code: [{ required: true, message: () => t('validation.required', { field: t('dict.code') }), trigger: 'blur' }],
  name: [{ required: true, message: () => t('validation.required', { field: t('dict.name') }), trigger: 'blur' }],
}

// 数据项管理
const dataDrawerVisible = ref(false)
const currentType = ref<DictTypeVO | null>(null)
const dataList = ref<DictDataVO[]>([])
const dataLoading = ref(false)
const selectedData = ref<DictDataVO | null>(null)

const dataDialogVisible = ref(false)
const isEditingData = ref(false)
const editingDataId = ref<number | null>(null)
const dataSubmitting = ref(false)
const dataFormRef = ref<any>(null)
const dataForm = reactive({ label: '', value: '', sort: null as number | null, status: 1, tagType: '', defaultFlag: 0, remark: '' })
const dataRules = {
  label: [{ required: true, message: () => t('validation.required', { field: t('dict.label') }), trigger: 'blur' }],
  value: [{ required: true, message: () => t('validation.required', { field: t('dict.value') }), trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const res = await dictApi.list({
      keyword: filter.keyword || undefined,
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
function handleReset() { filter.keyword = ''; filter.status = ''; page.value = 1; fetchList() }

function showCreateType() {
  isEditingType.value = false
  editingTypeId.value = null
  typeForm.code = ''
  typeForm.name = ''
  typeForm.description = ''
  typeForm.sort = null
  typeForm.status = 1
  typeDialogVisible.value = true
}

function showEditType(row: DictTypeVO) {
  isEditingType.value = true
  editingTypeId.value = row.id
  typeForm.code = row.code
  typeForm.name = row.name
  typeForm.description = row.description || ''
  typeForm.sort = row.sort
  typeForm.status = row.status
  typeDialogVisible.value = true
}

async function saveType() {
  const valid = await typeFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEditingType.value && editingTypeId.value) {
      await dictApi.update(editingTypeId.value, {
        name: typeForm.name, description: typeForm.description, status: typeForm.status, sort: typeForm.sort ?? undefined,
      })
    } else {
      await dictApi.create({
        code: typeForm.code, name: typeForm.name, description: typeForm.description, status: typeForm.status, sort: typeForm.sort ?? undefined,
      })
    }
    typeDialogVisible.value = false
    ElMessage.success(t('common.save'))
    if (isEditingType.value) {
      dictStore.invalidate(typeForm.code)
    }
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function toggleTypeStatus(row: DictTypeVO) {
  await dictApi.toggleStatus(row.id)
  dictStore.invalidate(row.code)
  ElMessage.success(t('common.save'))
  fetchList()
}

async function handleDeleteType(row: DictTypeVO) {
  await ElMessageBox.confirm(t('dict.deleteTypeConfirm', { name: row.name }), t('common.confirm'), { type: 'warning' })
  await dictApi.delete(row.id)
  dictStore.invalidate(row.code)
  ElMessage.success(t('common.save'))
  fetchList()
}

// ==================== 数据项管理 ====================

async function showDataDrawer(row: DictTypeVO) {
  currentType.value = row
  dataDrawerVisible.value = true
  await refreshDataList()
}

async function refreshDataList() {
  if (!currentType.value) return
  dataLoading.value = true
  try {
    const res = await dictApi.getByCode(currentType.value.code)
    dataList.value = res.dataList || []
  } finally {
    dataLoading.value = false
  }
}

function resetDataForm() {
  dataForm.label = ''
  dataForm.value = ''
  dataForm.sort = null
  dataForm.status = 1
  dataForm.tagType = ''
  dataForm.defaultFlag = 0
  dataForm.remark = ''
}

function showCreateData() {
  isEditingData.value = false
  editingDataId.value = null
  resetDataForm()
  dataDialogVisible.value = true
}

function showEditData(row: DictDataVO) {
  isEditingData.value = true
  editingDataId.value = row.id
  dataForm.label = row.label
  dataForm.value = row.value
  dataForm.sort = row.sort
  dataForm.status = row.status
  dataForm.tagType = row.tagType || ''
  dataForm.defaultFlag = row.defaultFlag
  dataForm.remark = row.remark || ''
  selectedData.value = row
  dataDialogVisible.value = true
}

async function saveData() {
  const valid = await dataFormRef.value?.validate().catch(() => false)
  if (!valid) return
  dataSubmitting.value = true
  try {
    if (isEditingData.value && editingDataId.value) {
      await dictApi.updateData(editingDataId.value, {
        label: dataForm.label, value: dataForm.value, sort: dataForm.sort ?? undefined,
        status: dataForm.status, tagType: dataForm.tagType || undefined,
        defaultFlag: dataForm.defaultFlag, remark: dataForm.remark || undefined,
      })
    } else {
      if (!currentType.value) return
      await dictApi.addData({
        typeCode: currentType.value.code,
        label: dataForm.label, value: dataForm.value, sort: dataForm.sort ?? undefined,
        status: dataForm.status, tagType: dataForm.tagType || undefined,
        defaultFlag: dataForm.defaultFlag, remark: dataForm.remark || undefined,
      })
    }
    dataDialogVisible.value = false
    ElMessage.success(t('common.save'))
    if (currentType.value) {
      dictStore.invalidate(currentType.value.code)
    }
    refreshDataList()
  } finally {
    dataSubmitting.value = false
  }
}

async function handleDeleteData(row: DictDataVO) {
  await ElMessageBox.confirm(t('dict.deleteDataConfirm', { label: row.label }), t('common.confirm'), { type: 'warning' })
  await dictApi.deleteData(row.id)
  dictStore.invalidate(row.typeCode)
  ElMessage.success(t('common.save'))
  refreshDataList()
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.stats-summary {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
.summary-total { font-weight: 600; color: #409eff; }
.summary-healthy { font-weight: 600; color: #67c23a; }
.summary-offline { font-weight: 600; color: #c0c4cc; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
</style>
