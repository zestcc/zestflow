<template>
  <div class="chains-page">
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('chains.total') }} {{ chainList.length }}</span>
        <el-tag type="success" size="small" style="margin-left:8px">{{ $t('chains.enabled') }} {{ stats.enabled }}</el-tag>
        <el-tag type="danger" size="small">{{ $t('chains.disabled') }} {{ stats.disabled }}</el-tag>
        <el-select
          v-model="currentModuleId"
          filterable
          style="width:200px;margin-left:16px"
          :placeholder="$t('chains.selectModule')"
          @change="handleModuleChange"
        >
          <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate">{{ $t('chains.createChain') }}</el-button>
    </div>

    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item :label="$t('chains.keyword')">
        <el-input v-model="filter.keyword" :placeholder="$t('chains.keyword')" clearable style="width:200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="filter.status" :placeholder="$t('chains.total')" clearable style="width:100px">
          <el-option :label="$t('chains.enabled')" :value="1" />
          <el-option :label="$t('chains.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('chains.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('chains.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="chainList"
      v-loading="loading"
      stripe border
      style="width:100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="code" :label="$t('chains.code')" show-overflow-tooltip width="150" />
      <el-table-column prop="name" :label="$t('chains.name')" show-overflow-tooltip min-width="150" />
      <el-table-column prop="status" :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? $t('chains.enabled') : $t('chains.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="$t('chains.description')" show-overflow-tooltip min-width="160" />
      <el-table-column prop="createdAt" :label="$t('chains.createdAt')" width="170">
        <template #default="{ row }">{{ row.createdAt?.replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button text :type="row.status === 1 ? 'warning' : 'success'" size="small" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? $t('chains.disable') : $t('chains.enable') }}
          </el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
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
        @size-change="fetchList"
      />
    </div>

    <!-- 新建链弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="$t('chains.createChain')" width="500px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px" @submit.prevent>
        <el-form-item :label="$t('chains.code')" prop="code">
          <el-input v-model="createForm.code" maxlength="50" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.name')" prop="name">
          <el-input v-model="createForm.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.description')" prop="description">
          <el-input v-model="createForm.description" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="handleCreate">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑链弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="$t('common.edit')" width="500px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px" @submit.prevent>
        <el-form-item :label="$t('chains.name')" prop="name">
          <el-input v-model="editForm.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.description')" prop="description">
          <el-input v-model="editForm.description" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { chainApi, type ChainCreateDTO } from '@/api/chain'
import { moduleApi, type ModuleVO } from '@/api/module'

const { t } = useI18n()

const loading = ref(false)
const modules = ref<ModuleVO[]>([])
const currentModuleId = ref<number | undefined>(undefined)
const chainList = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filter = ref({ keyword: '', status: undefined as number | undefined })

const stats = computed(() => {
  const enabled = chainList.value.filter((c: any) => c.status === 1).length
  const disabled = chainList.value.filter((c: any) => c.status === 0).length
  return { enabled, disabled }
})

async function fetchModules() {
  try {
    modules.value = await moduleApi.list()
    if (modules.value.length > 0 && !currentModuleId.value) {
      currentModuleId.value = modules.value[0].id
    }
  } catch { /* ignore */ }
}

async function fetchList() {
  if (!currentModuleId.value) return
  loading.value = true
  try {
    const res = await chainApi.list({
      moduleId: currentModuleId.value,
      keyword: filter.value.keyword || undefined,
      status: filter.value.status,
      page: page.value,
      size: pageSize.value,
    })
    chainList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleModuleChange() { page.value = 1; fetchList() }
function handleSearch() { page.value = 1; fetchList() }
function handleReset() { filter.value = { keyword: '', status: undefined }; page.value = 1; fetchList() }

// 创建
const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const createFormRef = ref<any>(null)
const createForm = ref({ code: '', name: '', description: '' })
const createRules = {
  code: [{ required: true, message: () => t('validation.required', { field: t('chains.code') }), trigger: 'blur' }],
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
}

function openCreate() {
  createForm.value = { code: '', name: '', description: '' }
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    const dto: ChainCreateDTO = { code: createForm.value.code, name: createForm.value.name, description: createForm.value.description || undefined, moduleId: currentModuleId.value! }
    await chainApi.create(dto)
    ElMessage.success(t('chains.createChain') + '成功')
    createDialogVisible.value = false
    await fetchList()
  } finally { createSubmitting.value = false }
}

// 编辑
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<any>(null)
const editingId = ref<number | null>(null)
const editForm = ref({ name: '', description: '' })
const editRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
}

function openEdit(row: any) {
  editingId.value = row.id
  editForm.value = { name: row.name, description: row.description || '' }
  editDialogVisible.value = true
}

async function handleEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingId.value) return
  editSubmitting.value = true
  try {
    await chainApi.update(editingId.value, { name: editForm.value.name, description: editForm.value.description || undefined })
    ElMessage.success(t('common.edit') + '成功')
    editDialogVisible.value = false
    await fetchList()
  } finally { editSubmitting.value = false }
}

// 启停
async function handleToggleStatus(row: any) {
  await chainApi.toggleStatus(row.id)
  ElMessage.success(row.status === 1 ? t('chains.disable') + '成功' : t('chains.enable') + '成功')
  await fetchList()
}

// 删除
function handleDelete(row: any) {
  ElMessageBox.confirm(t('chains.deleteConfirm', { name: row.name }), t('common.delete'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
  ).then(async () => {
    await chainApi.delete(row.id)
    ElMessage.success(t('common.delete') + '成功')
    await fetchList()
  }).catch(() => {})
}

onMounted(async () => {
  await fetchModules()
  fetchList()
})
</script>

<style scoped>
.chains-page {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.stats-summary {
  display: flex;
  align-items: center;
  font-size: 14px;
}
</style>
