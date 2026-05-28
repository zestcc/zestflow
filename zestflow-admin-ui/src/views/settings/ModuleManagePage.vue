<template>
  <div class="module-manage">
    <div class="page-header">
      <div class="stats-summary">
        <span class="summary-item summary-total">模块 {{ moduleList.length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-item summary-healthy">正常 {{ moduleList.reduce((s, m) => s + m.executorHealthy, 0) }}</span>
        <el-divider direction="vertical" />
        <span class="summary-item summary-error">异常 {{ moduleList.reduce((s, m) => s + m.executorError, 0) }}</span>
        <el-divider direction="vertical" />
        <span class="summary-item summary-offline">离线 {{ moduleList.reduce((s, m) => s + m.executorOffline, 0) }}</span>
      </div>
      <el-button type="primary" @click="openCreate">
        {{ $t('settings.createModule') }}
      </el-button>
    </div>

    <!-- 筛选条件 -->
    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item label="模块编号">
        <el-input v-model="filter.code" placeholder="输入模块编号" clearable style="width:140px" />
      </el-form-item>
      <el-form-item label="模块名称">
        <el-input v-model="filter.name" placeholder="输入模块名称" clearable style="width:140px" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="filter.owner" placeholder="输入负责人" clearable style="width:120px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filter.status" placeholder="全部" clearable style="width:100px">
          <el-option :label="$t('settings.enabled')" :value="1" />
          <el-option :label="$t('settings.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行器">
        <el-checkbox-group v-model="filter.executorStatuses">
          <el-checkbox label="正常" value="1" />
          <el-checkbox label="异常" value="2" />
          <el-checkbox label="离线" value="0" />
        </el-checkbox-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="paginatedList"
      v-loading="loading"
      stripe border
      style="width: 100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="code" :label="$t('settings.moduleCode')" show-overflow-tooltip />
      <el-table-column prop="name" :label="$t('settings.moduleName')" show-overflow-tooltip />
      <el-table-column prop="description" :label="$t('settings.moduleDesc')" show-overflow-tooltip />
      <el-table-column prop="status" :label="$t('common.status')" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('settings.enabled') : $t('settings.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="owner" :label="$t('settings.moduleOwner')" />
      <el-table-column label="全部" align="center">
        <template #default="{ row }">
          <span style="color:#409eff;font-weight:600">{{ row.executorTotal }}</span>
        </template>
      </el-table-column>
      <el-table-column label="正常" align="center">
        <template #default="{ row }">
          <span style="color:#67c23a;font-weight:600">{{ row.executorHealthy }}</span>
        </template>
      </el-table-column>
      <el-table-column label="异常" align="center">
        <template #default="{ row }">
          <span style="color:#f56c6c;font-weight:600">{{ row.executorError }}</span>
        </template>
      </el-table-column>
      <el-table-column label="离线" align="center">
        <template #default="{ row }">
          <span style="color:#c0c4cc;font-weight:600">{{ row.executorOffline }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openExecutors(row)">执行器</el-button>
          <el-button text type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="filteredList.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>

    <!-- 执行器列表弹窗 -->
    <el-dialog
      v-model="executorDialogVisible"
      :title="executorDialogTitle"
      width="1200px"
      top="3vh"
      :close-on-click-modal="false"
    >
      <div style="display:flex;align-items:center;gap:4px;margin-bottom:12px;font-size:13px" v-if="executorList.length > 0">
        <span style="color:#409eff;font-weight:600">全部 {{ executorList.length }}</span>
        <el-divider direction="vertical" />
        <span style="color:#67c23a;font-weight:600">正常 {{ executorList.filter(e => e.status === 1).length }}</span>
        <el-divider direction="vertical" />
        <span style="color:#f56c6c;font-weight:600">异常 {{ executorList.filter(e => e.status === 2).length }}</span>
        <el-divider direction="vertical" />
        <span style="color:#c0c4cc;font-weight:600">离线 {{ executorList.filter(e => e.status === 0).length }}</span>
      </div>
      <el-table
        :data="paginatedExecutorList"
        v-loading="executorLoading"
        stripe border
        style="width: 100%"
        max-height="440"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
      >
        <el-table-column prop="executorId" label="执行器ID" show-overflow-tooltip />
        <el-table-column label="地址" show-overflow-tooltip>
          <template #default="{ row }">
            <span style="font-family:monospace;font-size:13px">{{ row.executorHost }}:{{ row.executorPort }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small" effect="dark">正常</el-tag>
            <el-tag v-else-if="row.status === 2" type="danger" size="small" effect="dark">异常</el-tag>
            <el-tag v-else type="info" size="small" effect="dark">离线</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后心跳" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.lastHeartbeat) }}
          </template>
        </el-table-column>
        <el-table-column label="注册时间" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              text type="success" size="small"
              :loading="togglingId === row.id"
              @click="toggleStatus(row, 1)"
            >上线</el-button>
            <el-button
              v-if="row.status !== 0"
              text type="warning" size="small"
              :loading="togglingId === row.id"
              @click="toggleStatus(row, 0)"
            >下线</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:12px" v-if="executorList.length > 0">
        <el-pagination
          v-model:current-page="executorPage"
          v-model:page-size="executorPageSize"
          :total="executorList.length"
          :page-sizes="[5, 10, 20]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
      <el-empty v-if="executorList.length === 0 && !executorLoading" :image-size="80">
        <template #description>
          <span style="font-size:14px;color:#606266">暂无注册的执行器</span><br />
          <span style="font-size:12px;color:#909399">Executor 启动后将自动注册</span>
        </template>
      </el-empty>
    </el-dialog>

    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('settings.editModule') : $t('settings.createModule')"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" @submit.prevent>
        <el-form-item :label="$t('settings.moduleCode')" prop="code">
          <el-input v-model="form.code" :disabled="isEditing" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$t('settings.moduleName')" prop="name">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$t('settings.moduleDesc')" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
        <el-form-item :label="$t('common.status')" prop="status">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
        <el-form-item :label="$t('settings.moduleOwner')" prop="owner">
          <el-input v-model="form.owner" maxlength="50" />
        </el-form-item>
        <el-form-item :label="$t('settings.sortOrder')" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { moduleApi } from '@/api/module'
import type { ModuleVO, ModuleCreateDTO, ModuleUpdateDTO, ExecutorRegistryVO } from '@/api/module'

const { t } = useI18n()

const loading = ref(false)
const moduleList = ref<ModuleVO[]>([])

// 筛选条件
const filter = ref({
  code: '',
  name: '',
  owner: '',
  status: '' as number | string,
  executorStatuses: [] as string[],
})

// 模块列表分页
const page = ref(1)
const pageSize = ref(10)

// 执行器分页
const executorPage = ref(1)
const executorPageSize = ref(10)

const filteredList = computed(() => {
  let list = moduleList.value
  const f = filter.value
  if (f.code) list = list.filter(m => m.code.includes(f.code))
  if (f.name) list = list.filter(m => m.name.includes(f.name))
  if (f.owner) list = list.filter(m => m.owner && m.owner.includes(f.owner))
  if (f.status === 0 || f.status === 1) list = list.filter(m => m.status === f.status)
  if (f.executorStatuses.length > 0) {
    list = list.filter(m => {
      return f.executorStatuses.some(s => {
        if (s === '0') return m.executorOffline > 0
        if (s === '1') return m.executorHealthy > 0
        if (s === '2') return m.executorError > 0
        return false
      })
    })
  }
  return list
})

const paginatedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

const paginatedExecutorList = computed(() => {
  const start = (executorPage.value - 1) * executorPageSize.value
  return executorList.value.slice(start, start + executorPageSize.value)
})

function handleSearch() {
  page.value = 1
}

function handleReset() {
  filter.value = { code: '', name: '', owner: '', status: '', executorStatuses: [] }
  page.value = 1
}

// 执行器弹窗
const executorDialogVisible = ref(false)
const executorLoading = ref(false)
const executorDialogTitle = ref('')
const executorList = ref<ExecutorRegistryVO[]>([])
const togglingId = ref<number | null>(null)
const dialogVisible = ref(false)
const submitting = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<any>(null)

const form = ref<ModuleCreateDTO>({
  code: '',
  name: '',
  description: '',
  status: 1,
  owner: '',
  sortOrder: undefined,
})

const rules = {
  code: [
    { required: true, message: () => t('validation.required', { field: t('settings.moduleCode') }), trigger: 'blur' },
    { min: 2, max: 50, message: '编码长度 2-50 位', trigger: 'blur' },
  ],
  name: [
    { required: true, message: () => t('validation.required', { field: t('settings.moduleName') }), trigger: 'blur' },
    { min: 2, max: 100, message: '名称长度 2-100 位', trigger: 'blur' },
  ],
}

function formatTime(t: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ')
}

async function fetchList() {
  loading.value = true
  try {
    moduleList.value = await moduleApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  form.value = { code: '', name: '', description: '', status: 1, owner: '', sortOrder: undefined }
  dialogVisible.value = true
}

function openEdit(row: ModuleVO) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    code: row.code,
    name: row.name,
    description: row.description,
    status: row.status,
    owner: row.owner,
    sortOrder: row.sortOrder,
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  // 编辑时如果停用模块，检查是否存在未下线的执行器
  if (isEditing.value && editingId.value && form.value.status === 0) {
    const executors = await moduleApi.listExecutors(editingId.value)
    const healthy = executors.filter(e => e.status === 1).length
    const error = executors.filter(e => e.status === 2).length
    if (healthy > 0 || error > 0) {
      try {
        await ElMessageBox.confirm(
          `该模块下还有 ${healthy} 个在线、${error} 个异常执行器，确定要停用吗？`,
          '停用确认',
          { confirmButtonText: '确定停用', cancelButtonText: '取消', type: 'warning' },
        )
      } catch {
        return
      }
    }
  }

  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      const dto: ModuleUpdateDTO = { ...form.value }
      delete (dto as any).code
      await moduleApi.update(editingId.value, dto)
      ElMessage.success(t('common.save') + '成功')
    } else {
      await moduleApi.create(form.value)
      ElMessage.success(t('settings.createModule') + '成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

function handleDelete(row: ModuleVO) {
  ElMessageBox.confirm(
    t('settings.deleteConfirm', { name: row.name }),
    t('settings.confirmDelete'),
    { confirmButtonText: t('settings.yes'), cancelButtonText: t('settings.no'), type: 'warning' },
  ).then(async () => {
    await moduleApi.delete(row.id)
    ElMessage.success(t('common.delete') + '成功')
    await fetchList()
  }).catch(() => {})
}

async function openExecutors(row: ModuleVO) {
  executorDialogTitle.value = `执行器列表 - ${row.name} (${row.code})`
  executorList.value = []
  executorDialogVisible.value = true
  executorLoading.value = true
  try {
    executorList.value = await moduleApi.listExecutors(row.id)
  } finally {
    executorLoading.value = false
  }
}

async function toggleStatus(row: ExecutorRegistryVO, newStatus: number) {
  togglingId.value = row.id
  try {
    await moduleApi.updateExecutorStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '上线成功' : '已下线')
    const idx = executorList.value.findIndex(e => e.id === row.id)
    if (idx !== -1) executorList.value[idx].status = newStatus
  } catch {
    executorList.value = await moduleApi.listExecutors(row.moduleId)
  } finally {
    togglingId.value = null
  }
}

onMounted(fetchList)
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

.summary-item { font-weight: 600; }
.summary-total { color: #409eff; }
.summary-healthy { color: #67c23a; }
.summary-error { color: #f56c6c; }
.summary-offline { color: #c0c4cc; }
</style>
