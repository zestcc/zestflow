<template>
  <div class="dict-types-page">
    <div class="page-header">
      <div class="page-header-row">
        <div class="page-stats-row">
          <span class="summary-total">{{ $t('dict.total') }} {{ typeList.length }}</span>
        </div>
        <el-button type="primary" @click="showCreateType">{{ $t('dict.createType') }}</el-button>
      </div>
    </div>

    <div class="dict-layout">
      <aside class="dict-type-panel">
        <el-input
          v-model="typeKeyword"
          :placeholder="$t('dict.filterPlaceholder')"
          clearable
          class="type-search"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-scrollbar class="type-scroll">
          <div
            v-for="row in filteredTypes"
            :key="row.id"
            class="type-item"
            :class="{ active: currentType?.id === row.id }"
            @click="selectType(row)"
          >
            <div class="type-item-main">
              <span class="type-code">{{ row.code }}</span>
              <el-tag v-if="row.status === 0" size="small" type="info">{{ $t('dict.disabled') }}</el-tag>
            </div>
            <div class="type-item-sub">{{ row.name }}</div>
          </div>
          <el-empty v-if="!filteredTypes.length && !loading" :description="$t('common.noData')" :image-size="64" />
        </el-scrollbar>
      </aside>

      <main v-if="currentType" class="dict-data-panel">
        <div class="data-panel-header">
          <div>
            <h3 class="data-panel-title">{{ currentType.code }}</h3>
            <p class="data-panel-desc">{{ currentType.name }}</p>
          </div>
          <div class="data-panel-actions">
            <el-radio-group v-model="dataViewMode" size="small">
              <el-radio-button value="tree">{{ $t('dict.viewTree') }}</el-radio-button>
              <el-radio-button value="table">{{ $t('dict.viewTable') }}</el-radio-button>
            </el-radio-group>
            <el-button type="primary" size="small" @click="showCreateData()">{{ $t('dict.addData') }}</el-button>
            <el-button text size="small" type="primary" @click="showEditType(currentType)">{{ $t('dict.editType') }}</el-button>
          </div>
        </div>

        <div v-if="dataViewMode === 'tree'" v-loading="dataLoading" class="dict-tree-wrap">
          <el-tree
            :data="dataTree"
            node-key="nodeKey"
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'label', children: 'children' }"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <div class="tree-node-main">
                  <span class="tree-label">{{ data.label }}</span>
                  <code class="tree-value">{{ data.value }}</code>
                  <el-tag v-if="data.virtualNode" size="small" type="warning">{{ $t('dict.virtualGroup') }}</el-tag>
                  <el-tag v-else-if="data.status === 0" size="small" type="info">{{ $t('dict.disabled') }}</el-tag>
                  <el-tag v-if="data.defaultFlag === 1" size="small" type="success">{{ $t('dict.default') }}</el-tag>
                </div>
                <div v-if="!data.virtualNode" class="tree-node-actions">
                  <el-button text size="small" type="primary" @click.stop="showCreateData(data.id)">{{ $t('dict.addChild') }}</el-button>
                  <el-button text size="small" type="primary" @click.stop="showEditData(data)">{{ $t('common.edit') }}</el-button>
                  <el-button text size="small" type="danger" @click.stop="handleDeleteData(data)">{{ $t('common.delete') }}</el-button>
                </div>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="!dataLoading && !dataTree.length" :description="$t('dict.noDataItems')" />
        </div>

        <ResponsiveTable
          v-else
          :data="dataList"
          :columns="dataColumns"
          :loading="dataLoading"
          row-key="id"
          :show-actions="true"
          :actions-label="$t('common.actions')"
          :actions-width="140"
        >
          <template #status="{ row }">
            <el-tag :type="enableStatusTagType(row.status)" size="small">
              {{ enableStatusLabel(row.status) }}
            </el-tag>
          </template>
          <template #tagType="{ row }">
            <el-tag v-if="row.tagType" :type="row.tagType" size="small">{{ row.tagType }}</el-tag>
            <span v-else>-</span>
          </template>
          <template #defaultFlag="{ row }">
            <el-tag v-if="row.defaultFlag === 1" type="success" size="small">{{ $t('dict.yes') }}</el-tag>
            <span v-else>-</span>
          </template>
          <template #actions="{ row }">
            <el-button text size="small" type="primary" class="action-btn" @click="showEditData(row)">{{ $t('common.edit') }}</el-button>
            <el-button text size="small" type="danger" class="action-btn" @click="handleDeleteData(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </ResponsiveTable>
      </main>

      <main v-else class="dict-data-panel dict-data-empty">
        <el-empty :description="$t('dict.selectTypeHint')" />
      </main>
    </div>

    <!-- 新建/编辑字典类型 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="isEditingType ? $t('dict.editType') : $t('dict.createType')"
      :width="600"
      :close-on-click-modal="false"
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

    <!-- 新建/编辑数据项 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="isEditingData ? $t('dict.editData') : $t('dict.addData')"
      :width="560"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="110px">
        <el-form-item :label="$t('dict.label')" prop="label">
          <el-input v-model="dataForm.label" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('dict.value')" prop="value">
          <el-input v-model="dataForm.value" maxlength="128" />
        </el-form-item>
        <el-form-item :label="$t('dict.parentId')">
          <el-tree-select
            v-model="dataForm.parentId"
            :data="parentTreeOptions"
            :props="{ value: 'id', label: 'label', children: 'children' }"
            check-strictly
            clearable
            filterable
            :placeholder="$t('dict.parentIdPlaceholder')"
            style="width:100%"
          />
        </el-form-item>
        <el-divider content-position="left">{{ $t('dict.crossCascade') }}</el-divider>
        <el-form-item :label="$t('dict.parentTypeCode')">
          <el-select
            v-model="dataForm.parentTypeCode"
            clearable
            filterable
            allow-create
            :placeholder="$t('dict.parentTypeCode')"
            style="width:100%"
            @change="onParentTypeChange"
          >
            <el-option v-for="t in typeList" :key="t.code" :label="`${t.code} (${t.name})`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dict.parentValue')">
          <el-select
            v-model="dataForm.parentValue"
            clearable
            filterable
            allow-create
            :disabled="!dataForm.parentTypeCode"
            :placeholder="$t('dict.parentValuePlaceholder')"
            style="width:100%"
          >
            <el-option v-for="opt in crossParentOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
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
        <el-form-item :label="$t('dict.extra')">
          <el-input v-model="dataForm.extra" type="textarea" :rows="4" />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { dictApi, type DictTypeVO, type DictDataVO, type DictDataTreeVO } from '@/api/dict'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useDict } from '@/composables/useDict'
import { useDictLabel } from '@/composables/useDictLabel'
import { useDictStore } from '@/stores/dict'
import { buildParentTreeOptions } from '@/utils/dictTreeUtils'

const { t } = useI18n()
const dictStore = useDictStore()
const { labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')
const { options: tagTypeOptions } = useDict('tag_type')

const dataColumns = computed(() => [
  { prop: 'label', label: t('dict.label'), minWidth: 100, showOverflowTooltip: true },
  { prop: 'value', label: t('dict.value'), minWidth: 100, showOverflowTooltip: true },
  { prop: 'parentId', label: t('dict.parentId'), width: 90, align: 'center' as const },
  { prop: 'parentTypeCode', label: t('dict.parentTypeCode'), width: 120, showOverflowTooltip: true },
  { prop: 'parentValue', label: t('dict.parentValue'), width: 110, showOverflowTooltip: true },
  { prop: 'sort', label: t('dict.sort'), width: 60, align: 'center' as const },
  { prop: 'status', label: t('common.status'), width: 70, align: 'center' as const },
  { prop: 'tagType', label: t('dict.tagType'), width: 100, align: 'center' as const },
  { prop: 'defaultFlag', label: t('dict.default'), width: 90, align: 'center' as const },
])

const loading = ref(false)
const typeList = ref<DictTypeVO[]>([])
const typeKeyword = ref('')
const currentType = ref<DictTypeVO | null>(null)
const dataViewMode = ref<'tree' | 'table'>('tree')
const dataList = ref<DictDataVO[]>([])
const dataTree = ref<DictDataTreeVO[]>([])
const dataLoading = ref(false)
const crossParentOptions = ref<{ label: string; value: string }[]>([])

const filteredTypes = computed(() => {
  const kw = typeKeyword.value.trim().toLowerCase()
  if (!kw) return typeList.value
  return typeList.value.filter(
    (row) => row.code.toLowerCase().includes(kw) || row.name.toLowerCase().includes(kw),
  )
})

const parentTreeOptions = computed(() =>
  buildParentTreeOptions(dataTree.value, isEditingData.value ? editingDataId.value ?? undefined : undefined),
)

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

const dataDialogVisible = ref(false)
const isEditingData = ref(false)
const editingDataId = ref<number | null>(null)
const dataSubmitting = ref(false)
const dataFormRef = ref<any>(null)
const dataForm = reactive({
  label: '', value: '', parentId: null as number | null,
  parentTypeCode: '', parentValue: '', sort: null as number | null,
  status: 1, tagType: '', defaultFlag: 0, remark: '', extra: '',
})
const dataRules = {
  label: [{ required: true, message: () => t('validation.required', { field: t('dict.label') }), trigger: 'blur' }],
  value: [{ required: true, message: () => t('validation.required', { field: t('dict.value') }), trigger: 'blur' }],
}

async function fetchTypeList() {
  loading.value = true
  try {
    const res = await dictApi.list({ page: 1, size: 500 })
    typeList.value = res.records || []
    if (!currentType.value && typeList.value.length) {
      selectType(typeList.value[0])
    } else if (currentType.value) {
      currentType.value = typeList.value.find((x) => x.id === currentType.value!.id) || typeList.value[0] || null
    }
  } finally {
    loading.value = false
  }
}

function selectType(row: DictTypeVO) {
  currentType.value = row
  refreshData()
}

async function refreshData() {
  if (!currentType.value) return
  dataLoading.value = true
  try {
    const [tree, detail] = await Promise.all([
      dictApi.getDictDataTree(currentType.value.code),
      dictApi.getByCode(currentType.value.code),
    ])
    dataTree.value = tree || []
    dataList.value = detail.dataList || []
  } finally {
    dataLoading.value = false
  }
}

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
    if (isEditingType.value) dictStore.invalidate(typeForm.code)
    await fetchTypeList()
  } finally {
    submitting.value = false
  }
}

function resetDataForm() {
  dataForm.label = ''
  dataForm.value = ''
  dataForm.parentId = null
  dataForm.parentTypeCode = ''
  dataForm.parentValue = ''
  dataForm.sort = null
  dataForm.status = 1
  dataForm.tagType = ''
  dataForm.defaultFlag = 0
  dataForm.remark = ''
  dataForm.extra = ''
  crossParentOptions.value = []
}

function showCreateData(parentId?: number) {
  isEditingData.value = false
  editingDataId.value = null
  resetDataForm()
  if (parentId) dataForm.parentId = parentId
  dataDialogVisible.value = true
}

function showEditData(row: DictDataVO | DictDataTreeVO) {
  isEditingData.value = true
  editingDataId.value = row.id
  dataForm.label = row.label
  dataForm.value = row.value
  dataForm.parentId = row.parentId ?? null
  dataForm.parentTypeCode = row.parentTypeCode || ''
  dataForm.parentValue = row.parentValue || ''
  dataForm.sort = row.sort
  dataForm.status = row.status
  dataForm.tagType = row.tagType || ''
  dataForm.defaultFlag = row.defaultFlag
  dataForm.remark = row.remark || ''
  dataForm.extra = row.extra || ''
  loadCrossParentOptions(dataForm.parentTypeCode)
  dataDialogVisible.value = true
}

async function onParentTypeChange(typeCode: string) {
  dataForm.parentValue = ''
  await loadCrossParentOptions(typeCode)
}

async function loadCrossParentOptions(typeCode: string) {
  if (!typeCode) {
    crossParentOptions.value = []
    return
  }
  const items = await dictApi.getDictData(typeCode)
  crossParentOptions.value = items.map((i) => ({ label: i.label, value: i.value }))
}

async function saveData() {
  const valid = await dataFormRef.value?.validate().catch(() => false)
  if (!valid) return
  dataSubmitting.value = true
  try {
    const common = {
      label: dataForm.label,
      value: dataForm.value,
      parentId: dataForm.parentId ?? undefined,
      parentTypeCode: dataForm.parentTypeCode || undefined,
      parentValue: dataForm.parentValue || undefined,
      sort: dataForm.sort ?? undefined,
      status: dataForm.status,
      tagType: dataForm.tagType || undefined,
      defaultFlag: dataForm.defaultFlag,
      remark: dataForm.remark || undefined,
      extra: dataForm.extra || undefined,
    }
    if (isEditingData.value && editingDataId.value) {
      await dictApi.updateData(editingDataId.value, {
        ...common,
        parentId: dataForm.parentId ?? -1,
      })
    } else {
      if (!currentType.value) return
      await dictApi.addData({ typeCode: currentType.value.code, ...common })
    }
    dataDialogVisible.value = false
    ElMessage.success(t('common.save'))
    if (currentType.value) dictStore.invalidate(currentType.value.code)
    refreshData()
  } finally {
    dataSubmitting.value = false
  }
}

async function handleDeleteData(row: DictDataVO | DictDataTreeVO) {
  await ElMessageBox.confirm(t('dict.deleteDataConfirm', { label: row.label }), t('common.confirm'), { type: 'warning' })
  await dictApi.deleteData(row.id)
  dictStore.invalidate(row.typeCode)
  ElMessage.success(t('common.save'))
  refreshData()
}

watch(dataViewMode, () => {
  if (currentType.value) refreshData()
})

onMounted(fetchTypeList)
</script>

<style scoped>
.summary-total { font-weight: 600; color: #409eff; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }

.dict-layout {
  display: flex;
  gap: 16px;
  min-height: 520px;
  align-items: stretch;
}

.dict-type-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.type-search { margin: 12px; width: calc(100% - 24px); }
.type-scroll { flex: 1; padding: 0 8px 12px; }

.type-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.type-item:hover { background: var(--el-fill-color-light); }
.type-item.active { background: var(--el-color-primary-light-9); }
.type-item-main { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.type-code { font-family: monospace; font-weight: 600; font-size: 13px; color: var(--el-color-primary); }
.type-item-sub { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }

.dict-data-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}
.dict-data-empty { display: flex; align-items: center; justify-content: center; }

.data-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.data-panel-title { margin: 0; font-size: 18px; font-family: monospace; }
.data-panel-desc { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.data-panel-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }

.dict-tree-wrap { min-height: 360px; }
.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 8px 4px 0;
  min-width: 0;
}
.tree-node-main { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; min-width: 0; }
.tree-label { font-weight: 500; }
.tree-value { font-size: 12px; color: var(--el-text-color-secondary); background: var(--el-fill-color-light); padding: 1px 6px; border-radius: 4px; }
.tree-node-actions { flex-shrink: 0; }

@media (max-width: 768px) {
  .dict-layout { flex-direction: column; }
  .dict-type-panel { width: 100%; max-height: 240px; }
}
</style>
