<template>
  <div class="design-list">
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('design.total') }} {{ designList.length }}</span>
        <el-tag type="success" size="small" style="margin-left:8px">{{ $t('design.enabled') }} {{ stats.enabled }}</el-tag>
        <el-tag type="danger" size="small">{{ $t('design.disabled') }} {{ stats.disabled }}</el-tag>
        <el-select
          v-model="currentModuleId"
          filterable
          style="width:200px;margin-left:16px"
          :placeholder="$t('design.selectModule')"
          @change="handleModuleChange"
        >
          <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
      </div>
      <el-button type="primary" @click="openCreate">
        {{ $t('design.createDesign') }}
      </el-button>
    </div>

    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item :label="$t('design.name')">
        <el-input v-model="filter.keyword" :placeholder="$t('design.name')" clearable style="width:200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('design.status')">
        <el-select v-model="filter.status" :placeholder="$t('design.total')" clearable style="width:100px">
          <el-option :label="$t('design.enabled')" :value="1" />
          <el-option :label="$t('design.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('design.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('design.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="designList"
      v-loading="loading"
      stripe border
      style="width:100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="code" :label="$t('design.code')" width="160">
        <template #default="{ row }">
          <span style="color:#409eff;cursor:pointer;font-family:monospace;font-weight:600" @click="openDesignDetail(row)">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" :label="$t('design.name')" show-overflow-tooltip min-width="140" />
      <el-table-column prop="status" :label="$t('design.status')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? $t('design.enabled') : $t('design.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="designer" :label="$t('design.designer')" width="100" show-overflow-tooltip />
      <el-table-column :label="$t('design.module')" width="120" show-overflow-tooltip>
        <template #default>
          {{ currentModuleName }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('design.boundChainCodes')" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="!row.boundChainCodes" style="color:#909399;font-size:12px">{{ $t('design.noBindings') }}</span>
          <span v-else style="color:#409eff;cursor:pointer" @click="openChainDetailFromDesign(row)">{{ row.boundChainCodes }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="$t('design.description')" show-overflow-tooltip min-width="140" />
      <el-table-column prop="updatedBy" :label="$t('common.updatedBy')" width="120" show-overflow-tooltip />
      <el-table-column prop="updatedAt" :label="$t('design.updatedAt')" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.updatedAt?.replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="190" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" class="action-btn" @click="handleDesign(row)">
            {{ $t('design.design') }}
          </el-button>
          <el-button text type="primary" size="small" class="action-btn" @click="openBindDialog(row)">
            {{ $t('design.bind') }}
          </el-button>
          <el-button text type="primary" size="small" class="action-btn" @click="handleToggleStatus(row)">
            {{ row.status === 1 ? $t('design.disable') : $t('design.enable') }}
          </el-button>
          <el-button text type="primary" size="small" class="action-btn" @click="openEdit(row)">
            {{ $t('common.edit') }}
          </el-button>
          <el-button text type="danger" size="small" class="action-btn" @click="handleDelete(row)">
            {{ $t('design.delete') }}
          </el-button>
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

    <!-- 创建弹窗 -->
    <CreateDesignDialog
      v-model:visible="createDialogVisible"
      :module-options="modules"
      :default-module-id="currentModuleId"
      @saved="onDesignCreated"
    />

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="$t('design.editDesign')" width="600px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item :label="$t('design.code')">
          <el-input :model-value="editForm.code" disabled autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('design.name')" prop="name">
          <el-input v-model="editForm.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('design.designer')" prop="designer">
          <el-input v-model="editForm.designer" maxlength="50" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('design.description')" prop="description">
          <el-input v-model="editForm.description" type="textarea" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEdit">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 绑定弹窗 -->
    <el-dialog v-model="bindDialogVisible" :title="$t('design.bindToChain')" width="700px" :close-on-click-modal="false">
      <div v-if="bindTarget" style="margin-bottom:12px">
        <strong>{{ bindTarget.code }} - {{ bindTarget.name }}</strong>
      </div>
      <div style="margin-bottom:8px">
        <span style="font-weight:600;color:#606266">{{ $t('design.alreadyBound') }}：</span>
        <span v-if="boundChains.length === 0" style="color:#909399">{{ $t('design.noBindings') }}</span>
        <el-tag v-for="b in boundChains" :key="b.code" closable style="margin-right:6px;margin-bottom:4px" @close="handleUnbind(b.code)">
          {{ b.code }} - {{ b.name }}
        </el-tag>
      </div>
      <el-divider />
      <div style="margin-bottom:8px;font-weight:600;color:#606266">{{ $t('design.bindableChains') }}：</div>
      <el-table :data="bindableChains" stripe border style="width:100%" :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}">
        <el-table-column prop="code" :label="$t('chains.code')" width="120" />
        <el-table-column prop="name" :label="$t('chains.name')" min-width="150" />
        <el-table-column prop="status" :label="$t('chains.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? $t('chains.enabled') : $t('chains.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="80" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleBind(row.code)">{{ $t('design.bind') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bindDialogVisible = false">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 设计详情抽屉 -->
    <el-drawer v-model="designDrawerVisible" title="设计详情" :size="520" destroy-on-close>
      <template v-if="currentDesignDetail">
        <div style="padding:0 8px">
          <div style="font-size:20px;font-weight:600;color:#303133;margin-bottom:12px">{{ currentDesignDetail.name }}</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="编码">
              <el-tag size="small" style="font-family:monospace">{{ currentDesignDetail.code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentDesignDetail.status === 1 ? 'success' : 'danger'" size="small">
                {{ currentDesignDetail.status === 1 ? $t('design.enabled') : $t('design.disabled') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="设计者">
              {{ currentDesignDetail.designer || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="模块">
              {{ getModuleName(currentDesignDetail.moduleId) }}
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              {{ currentDesignDetail.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="绑定链">
              <template v-if="currentDesignDetail.boundChains && currentDesignDetail.boundChains.length > 0">
                <div v-for="c in currentDesignDetail.boundChains" :key="c.code" style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
                  <el-tag size="small" type="info" style="font-family:monospace">{{ c.code }}</el-tag>
                  <span style="font-size:13px;color:#303133">{{ c.name }}</span>
                  <el-tag :type="chainStatusTagType(c.status)" size="small" style="margin-left:auto">
                    {{ chainStatusLabel(c.status) }}
                  </el-tag>
                </div>
              </template>
              <span v-else style="color:#c0c4cc">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建人">{{ currentDesignDetail.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentDesignDetail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('common.updatedBy')">{{ currentDesignDetail.updatedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ currentDesignDetail.updatedAt?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </el-drawer>

    <!-- 链详情抽屉 -->
    <el-drawer v-model="chainDrawerVisible" title="链详情" :size="480" destroy-on-close>
      <template v-if="currentChainDetail">
        <div style="padding:0 8px">
          <div style="font-size:20px;font-weight:600;color:#303133;margin-bottom:12px">{{ currentChainDetail.name }}</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="编码">
              <el-tag size="small" style="font-family:monospace">{{ currentChainDetail.code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentChainDetail.status === 0 ? 'danger' : 'success'" size="small">
                {{ currentChainDetail.status === 0 ? $t('chains.disabled') : $t('chains.enabled') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="模块">
              {{ moduleNameMap[currentChainDetail.moduleId] || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              {{ currentChainDetail.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建人">{{ currentChainDetail.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentChainDetail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('common.updatedBy')">{{ currentChainDetail.updatedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ currentChainDetail.updatedAt?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { designApi, type DesignVO } from '@/api/design'
import type { ChainVO } from '@/api/chain'
import { moduleApi, type ModuleVO } from '@/api/module'
import CreateDesignDialog from '@/components/CreateDesignDialog.vue'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const modules = ref<ModuleVO[]>([])
const currentModuleId = ref<number | undefined>(undefined)
const designList = ref<DesignVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

function chainStatusTagType(status: number): string {
  return ['danger', 'info', 'warning', 'primary', 'success'][status] || 'info'
}
function chainStatusLabel(status: number): string {
  const labels = [t('chains.disabled'), t('chains.notDesigned'), t('chains.unpublished'), t('chains.publishing'), t('chains.published')]
  return labels[status] || '-'
}
const filter = ref({ keyword: '', status: undefined as number | undefined })

const currentModuleName = computed(() => {
  const m = modules.value.find(m => m.id === currentModuleId.value)
  return m ? m.name : ''
})

function getModuleName(moduleId: number | undefined): string {
  if (moduleId == null) return '-'
  const m = modules.value.find(m => m.id === moduleId)
  return m ? m.name : '-'
}

const stats = computed(() => {
  const enabled = designList.value.filter(c => c.status === 1).length
  const disabled = designList.value.filter(c => c.status === 0).length
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
    const res = await designApi.list({
      moduleId: currentModuleId.value,
      keyword: filter.value.keyword || undefined,
      status: filter.value.status,
      page: page.value,
      size: pageSize.value,
    })
    designList.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

function handleModuleChange() { page.value = 1; fetchList() }
function handleSearch() { page.value = 1; fetchList() }
function handleReset() { filter.value = { keyword: '', status: undefined }; page.value = 1; fetchList() }

// 创建
const createDialogVisible = ref(false)

function openCreate() {
  createDialogVisible.value = true
}

function onDesignCreated(design: DesignVO) {
  ElMessage.success(t('design.createDesign') + '成功，' + t('design.code') + '：' + design.code)
  createDialogVisible.value = false
  fetchList()
}

// 编辑
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<any>(null)
const editingCode = ref<string | null>(null)
const editingModuleId = ref<number>(0)
const editForm = ref({ code: '', name: '', description: '', designer: '' })
const editRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('design.name') }), trigger: 'blur' }],
}

function openEdit(row: DesignVO) {
  editingCode.value = row.code
  editingModuleId.value = row.moduleId
  editForm.value = {
    code: row.code,
    name: row.name,
    description: row.description || '',
    designer: row.designer || '',
  }
  editDialogVisible.value = true
}

async function handleEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingCode.value) return
  editSubmitting.value = true
  try {
    await designApi.update(editingCode.value, {
      name: editForm.value.name,
      description: editForm.value.description || undefined,
      designer: editForm.value.designer || undefined,
      moduleId: editingModuleId.value,
    })
    ElMessage.success(t('common.edit') + '成功')
    editDialogVisible.value = false
    await fetchList()
  } finally { editSubmitting.value = false }
}

// 启停 / 删除
async function handleToggleStatus(row: DesignVO) {
  await designApi.toggleStatus(row.code, row.moduleId)
  ElMessage.success(row.status === 1 ? t('design.disable') + '成功' : t('design.enable') + '成功')
  await fetchList()
}

function handleDelete(row: DesignVO) {
  ElMessageBox.confirm(t('design.deleteConfirm', { name: row.name }), t('design.delete'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
  ).then(async () => {
    await designApi.delete(row.code, row.moduleId)
    ElMessage.success(t('design.delete') + '成功')
    await fetchList()
  }).catch(() => {})
}

// 设计 → 跳转编辑器
function handleDesign(row: DesignVO) {
  router.push({ name: 'DesignEditor', params: { id: row.code }, query: { moduleId: row.moduleId } })
}

// 绑定
const bindDialogVisible = ref(false)
const bindTarget = ref<DesignVO | null>(null)
const boundChains = ref<ChainVO[]>([])
const bindableChains = ref<ChainVO[]>([])

async function openBindDialog(row: DesignVO) {
  bindTarget.value = row
  bindDialogVisible.value = true
  try {
    const [bindings, bindable] = await Promise.all([
      designApi.getBindings(row.code, row.moduleId),
      designApi.getBindable(row.code, row.moduleId),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
  } catch { /* ignore */ }
}

async function handleBind(chainCode: string) {
  if (!bindTarget.value) return
  try {
    await designApi.bind(bindTarget.value.code, chainCode, bindTarget.value.moduleId)
    ElMessage.success(t('design.bind') + '成功')
    const [bindings, bindable] = await Promise.all([
      designApi.getBindings(bindTarget.value.code, bindTarget.value.moduleId),
      designApi.getBindable(bindTarget.value.code, bindTarget.value.moduleId),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
    await fetchList()
  } catch { /* ignore */ }
}

async function handleUnbind(chainCode: string) {
  if (!bindTarget.value) return
  try {
    await designApi.unbind(bindTarget.value.code, chainCode, bindTarget.value.moduleId)
    ElMessage.success(t('design.unbind') + '成功')
    const [bindings, bindable] = await Promise.all([
      designApi.getBindings(bindTarget.value.code, bindTarget.value.moduleId),
      designApi.getBindable(bindTarget.value.code, bindTarget.value.moduleId),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
    await fetchList()
  } catch { /* ignore */ }
}

// 设计详情抽屉
const designDrawerVisible = ref(false)
const currentDesignDetail = ref<DesignVO | null>(null)
async function openDesignDetail(row: DesignVO) {
  designDrawerVisible.value = true
  currentDesignDetail.value = null
  try {
    currentDesignDetail.value = await designApi.getByCode(row.code, row.moduleId)
  } catch { /* ignore */ }
}

// 链详情抽屉
const chainDrawerVisible = ref(false)
const currentChainDetail = ref<any>(null)
const moduleNameMap = computed(() => {
  const map: Record<number, string> = {}
  modules.value.forEach(m => { map[m.id] = m.name })
  return map
})
function openChainDetail(chain: any) {
  currentChainDetail.value = chain
  chainDrawerVisible.value = true
}

async function openChainDetailFromDesign(row: DesignVO) {
  try {
    const detail = await designApi.getByCode(row.code, row.moduleId)
    const chains = detail.boundChains
    if (chains && chains.length > 0) {
      currentChainDetail.value = chains[0]
      chainDrawerVisible.value = true
    }
  } catch { /* ignore */ }
}

onMounted(async () => {
  await fetchModules()
  await fetchList()
})
</script>

<style scoped>
.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
}
.stats-summary {
  display: flex; align-items: center; font-size: 14px;
}
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
