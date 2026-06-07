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
        <div class="type-panel-head">
          <span class="type-panel-title">{{ $t('dict.typePanelTitle') }}</span>
          <el-tag size="small" type="info" round>{{ typeList.length }}</el-tag>
        </div>
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
        <el-scrollbar v-loading="loading" class="type-scroll">
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
          <div class="data-panel-meta">
            <div class="data-panel-title-row">
              <h3 class="data-panel-title">{{ currentType.code }}</h3>
              <el-tag size="small" type="primary" effect="plain">{{ $t('dict.dataCount', { n: dataList.length }) }}</el-tag>
            </div>
            <p v-if="currentType.description || currentType.name" class="data-panel-desc">
              {{ currentType.description || currentType.name }}
            </p>
          </div>
          <div class="data-panel-actions">
            <el-radio-group v-model="dataViewMode" size="small">
              <el-radio-button value="table">{{ $t('dict.viewTable') }}</el-radio-button>
              <el-radio-button value="tree">{{ $t('dict.viewTree') }}</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="showCreateData()">{{ $t('dict.addData') }}</el-button>
            <el-button @click="showEditType(currentType)">{{ $t('dict.editType') }}</el-button>
          </div>
        </div>

        <el-form inline size="default" class="data-filter-form" @submit.prevent>
          <el-form-item :label="$t('common.keyword')">
            <el-input
              v-model="dataKeyword"
              :placeholder="$t('dict.dataFilterPlaceholder')"
              clearable
              class="page-filter-control"
            />
          </el-form-item>
        </el-form>

        <ResponsiveTable
          v-if="dataViewMode === 'table'"
          :data="filteredDataList"
          :columns="dataColumns"
          :loading="dataLoading"
          row-key="id"
          :show-actions="true"
          :actions-label="$t('common.actions')"
          :actions-width="160"
        >
          <template #label="{ row }">
            <span class="cell-label">{{ row.label }}</span>
          </template>
          <template #value="{ row }">
            <code class="cell-value">{{ row.value }}</code>
          </template>
          <template #status="{ row }">
            <el-tag :type="enableStatusTagType(row.status)" size="small">
              {{ enableStatusLabel(row.status) }}
            </el-tag>
          </template>
          <template #tagType="{ row }">
            <el-tag v-if="row.tagType" :type="row.tagType" size="small">{{ row.tagType }}</el-tag>
            <span v-else class="cell-muted">-</span>
          </template>
          <template #defaultFlag="{ row }">
            <el-tag v-if="row.defaultFlag === 1" type="success" size="small">{{ $t('dict.yes') }}</el-tag>
            <span v-else class="cell-muted">-</span>
          </template>
          <template #actions="{ row }">
            <el-button link type="primary" size="small" @click="showEditData(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteData(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </ResponsiveTable>

        <div v-else v-loading="dataLoading" class="dict-tree-table-wrap">
          <el-table
            :data="filteredDataTree"
            row-key="nodeKey"
            default-expand-all
            stripe
            border
            style="width: 100%"
            :header-cell-style="tableHeaderStyle"
            :tree-props="{ children: 'children' }"
            :empty-text="$t('dict.noDataItems')"
          >
            <el-table-column prop="sort" :label="$t('dict.sort')" width="72" align="center" />
            <el-table-column prop="label" :label="$t('dict.label')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="cell-label">{{ row.label }}</span>
                <el-tag v-if="row.virtualNode" size="small" type="warning" class="cell-tag">{{ $t('dict.virtualGroup') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="value" :label="$t('dict.value')" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <code v-if="row.value" class="cell-value">{{ row.value }}</code>
                <span v-else class="cell-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column v-if="showParentColumns" prop="parentValue" :label="$t('dict.parentValue')" width="120" show-overflow-tooltip />
            <el-table-column :label="$t('common.status')" width="88" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.virtualNode" size="small" type="info">-</el-tag>
                <el-tag v-else :type="enableStatusTagType(row.status)" size="small">
                  {{ enableStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('dict.default')" width="88" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.defaultFlag === 1" type="success" size="small">{{ $t('dict.yes') }}</el-tag>
                <span v-else class="cell-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('common.actions')" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="!row.virtualNode">
                  <el-button link type="primary" size="small" @click="showCreateData(row.id)">{{ $t('dict.addChild') }}</el-button>
                  <el-button link type="primary" size="small" @click="showEditData(row)">{{ $t('common.edit') }}</el-button>
                  <el-button link type="danger" size="small" @click="handleDeleteData(row)">{{ $t('common.delete') }}</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
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
      :width="isAiProviderType ? 720 : 560"
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
        <template v-if="!isAiProviderType">
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
        </template>
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
        <AiProviderExtraForm
          v-if="isAiProviderType"
          ref="aiProviderExtraFormRef"
          v-model="dataForm.extra"
          @tier-change="onAiProviderTierChange"
        />
        <el-form-item v-else :label="$t('dict.extra')">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { dictApi, type DictTypeVO, type DictDataVO, type DictDataTreeVO } from '@/api/dict'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import type { ResponsiveTableColumn } from '@/components/ResponsiveTable.vue'
import AiProviderExtraForm from '@/components/settings/AiProviderExtraForm.vue'
import { useDict } from '@/composables/useDict'
import { parseAiProviderExtra, tagTypeForAiProviderTier } from '@/utils/aiProviderExtra'
import { useDictLabel } from '@/composables/useDictLabel'
import { useDictStore } from '@/stores/dict'
import { buildParentTreeOptions } from '@/utils/dictTreeUtils'

const { t } = useI18n()
const dictStore = useDictStore()
const { labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')
const { options: tagTypeOptions } = useDict('tag_type')

const tableHeaderStyle = { background: '#f5f7fa', color: '#303133', fontWeight: 600 }

const showParentColumns = computed(() =>
  dataList.value.some((row) => row.parentId || row.parentTypeCode || row.parentValue),
)

const dataColumns = computed((): ResponsiveTableColumn[] => {
  const cols: ResponsiveTableColumn[] = [
    { prop: 'sort', label: t('dict.sort'), width: 72, align: 'center' },
    { prop: 'label', label: t('dict.label'), minWidth: 120, showOverflowTooltip: true },
    { prop: 'value', label: t('dict.value'), minWidth: 120, showOverflowTooltip: true },
  ]
  if (showParentColumns.value) {
    cols.push(
      { prop: 'parentTypeCode', label: t('dict.parentTypeCode'), width: 130, showOverflowTooltip: true },
      { prop: 'parentValue', label: t('dict.parentValue'), width: 110, showOverflowTooltip: true },
    )
  }
  cols.push(
    { prop: 'status', label: t('common.status'), width: 88, align: 'center' },
    { prop: 'tagType', label: t('dict.tagType'), width: 100, align: 'center' },
    { prop: 'defaultFlag', label: t('dict.default'), width: 88, align: 'center' },
  )
  return cols
})

const loading = ref(false)
const typeList = ref<DictTypeVO[]>([])
const typeKeyword = ref('')
const dataKeyword = ref('')
const currentType = ref<DictTypeVO | null>(null)
const dataViewMode = ref<'tree' | 'table'>('table')
const dataList = ref<DictDataVO[]>([])
const dataTree = ref<DictDataTreeVO[]>([])
const dataLoading = ref(false)
const crossParentOptions = ref<{ label: string; value: string }[]>([])
const aiProviderExtraFormRef = ref<{ syncToModel: () => string } | null>(null)

const filteredTypes = computed(() => {
  const kw = typeKeyword.value.trim().toLowerCase()
  if (!kw) return typeList.value
  return typeList.value.filter(
    (row) => row.code.toLowerCase().includes(kw) || row.name.toLowerCase().includes(kw),
  )
})

function matchDataKeyword(row: { label?: string; value?: string }) {
  const kw = dataKeyword.value.trim().toLowerCase()
  if (!kw) return true
  return (row.label?.toLowerCase().includes(kw) ?? false) || (row.value?.toLowerCase().includes(kw) ?? false)
}

const filteredDataList = computed(() => dataList.value.filter(matchDataKeyword))

function filterTreeNodes(nodes: DictDataTreeVO[]): DictDataTreeVO[] {
  const kw = dataKeyword.value.trim().toLowerCase()
  if (!kw) return nodes
  const out: DictDataTreeVO[] = []
  for (const node of nodes) {
    const children = node.children?.length ? filterTreeNodes(node.children) : []
    if (matchDataKeyword(node) || children.length) {
      out.push({ ...node, children: children.length ? children : undefined })
    }
  }
  return out
}

const filteredDataTree = computed(() => filterTreeNodes(dataTree.value))

const parentTreeOptions = computed(() =>
  buildParentTreeOptions(dataTree.value, isEditingData.value ? editingDataId.value ?? undefined : undefined),
)

const isAiProviderType = computed(() => currentType.value?.code === 'ai_provider')

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
  dataKeyword.value = ''
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

function onAiProviderTierChange(_tier: string, tagType: string) {
  dataForm.tagType = tagType
}

function showCreateData(parentId?: number) {
  isEditingData.value = false
  editingDataId.value = null
  resetDataForm()
  if (parentId) dataForm.parentId = parentId
  if (isAiProviderType.value) {
    dataForm.tagType = tagTypeForAiProviderTier(parseAiProviderExtra('').tier)
  }
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
  if (isAiProviderType.value && !dataForm.tagType) {
    dataForm.tagType = tagTypeForAiProviderTier(parseAiProviderExtra(dataForm.extra).tier)
  }
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
    if (isAiProviderType.value) {
      aiProviderExtraFormRef.value?.syncToModel()
    }
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

onMounted(fetchTypeList)
</script>

<style scoped>
.summary-total { font-weight: 600; color: #409eff; }

.dict-layout {
  display: flex;
  gap: 16px;
  min-height: 560px;
  align-items: stretch;
}

.dict-type-panel {
  width: 260px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.type-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 0;
}

.type-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.type-search { margin: 12px; width: calc(100% - 24px); }
.type-scroll { flex: 1; padding: 0 8px 12px; }

.type-item {
  position: relative;
  padding: 10px 12px 10px 14px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.15s, color 0.15s;
  border-left: 3px solid transparent;
}
.type-item:hover { background: var(--el-fill-color-light); }
.type-item.active {
  background: var(--el-color-primary-light-9);
  border-left-color: var(--el-color-primary);
}
.type-item-main { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.type-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 600;
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.type-item.active .type-code { color: var(--el-color-primary); }
.type-item-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
}

.dict-data-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px 16px 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.dict-data-empty { display: flex; align-items: center; justify-content: center; }

.data-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.data-panel-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.data-panel-title {
  margin: 0;
  font-size: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 600;
}

.data-panel-desc {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.data-panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.data-filter-form {
  margin-bottom: 12px;
}

.data-filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.dict-tree-table-wrap {
  width: 100%;
  overflow-x: auto;
}

.cell-label { font-weight: 500; }
.cell-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 4px;
}
.cell-muted { color: var(--el-text-color-placeholder); }
.cell-tag { margin-left: 6px; vertical-align: middle; }

@media (max-width: 768px) {
  .dict-layout { flex-direction: column; }
  .dict-type-panel { width: 100%; max-height: 240px; }
  .data-panel-actions { width: 100%; }
}
</style>
