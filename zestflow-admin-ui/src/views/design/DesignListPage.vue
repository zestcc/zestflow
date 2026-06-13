<template>
  <div class="design-list">
    <ExecutorReadCacheAlert :stale="readCacheStale" />
    <div class="page-header">
      <div class="page-header-row">
        <div class="page-stats-row">
          <span style="font-weight:600;color:#409eff">{{ $t('design.total') }} {{ designList.length }}</span>
          <el-tag type="success" size="small">{{ $t('design.enabled') }} {{ stats.enabled }}</el-tag>
          <el-tag type="danger" size="small">{{ $t('design.disabled') }} {{ stats.disabled }}</el-tag>
        </div>
        <el-button type="primary" @click="openCreate">{{ $t('design.createDesign') }}</el-button>
      </div>
      <div class="page-toolbar">
        <el-form :model="filter" inline size="default" class="responsive-filter-form page-filters">
          <el-form-item>
            <el-select
              v-model="currentAppCode"
              filterable
              class="page-filter-control"
              :placeholder="$t('design.selectApp')"
              @change="handleAppChange"
            >
              <el-option v-for="m in apps" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="filter.keyword" :placeholder="$t('design.name')" clearable class="page-filter-control" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="filter.status" :placeholder="$t('design.total')" clearable class="page-filter-control--sm">
              <el-option
                v-for="item in enableStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.bindValue"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="filter-actions-item">
            <el-button type="primary" @click="handleSearch">{{ $t('design.search') }}</el-button>
            <el-button @click="handleReset">{{ $t('design.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <ResponsiveTable
      :data="designList"
      :columns="designColumns"
      :loading="loading"
      row-key="code"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="190"
    >
      <template #code="{ row }">
        <span class="code-link" @click="openDesignDetail(row)">{{ row.code }}</span>
      </template>
      <template #status="{ row }">
        <el-tag :type="enableStatusTagType(row.status)" size="small">
          {{ enableStatusLabel(row.status) }}
        </el-tag>
      </template>
      <template #appCode>{{ currentAppName }}</template>
      <template #boundChainCodes="{ row }">
        <span v-if="!row.boundChainCodes" style="color:#909399;font-size:12px">{{ $t('design.noBindings') }}</span>
        <span v-else class="code-link" @click="openChainDetailFromDesign(row)">{{ row.boundChainCodes }}</span>
      </template>
      <template #updatedAt="{ row }">{{ row.updatedAt?.replace('T', ' ') }}</template>
      <template #actions="{ row }">
        <el-button text type="primary" size="small" class="action-btn" @click="handleDesign(row)">{{ $t('design.design') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="openBindDialog(row)">{{ $t('design.bind') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="handleToggleStatus(row)">
          {{ row.status === 1 ? $t('design.disable') : $t('design.enable') }}
        </el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button text type="danger" size="small" class="action-btn" @click="handleDelete(row)">{{ $t('design.delete') }}</el-button>
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
        @size-change="fetchList"
      />
    </div>

    <!-- 创建弹窗 -->
    <CreateDesignDialog
      v-model:visible="createDialogVisible"
      :app-options="apps"
      :default-app-code="currentAppCode"
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
      <ResponsiveTable
        :data="bindableChains"
        :columns="bindableChainColumns"
        row-key="code"
        :show-actions="true"
        :actions-label="$t('common.actions')"
        :actions-width="80"
      >
        <template #status="{ row }">
          <el-tag :type="enableStatusTagType(row.status)" size="small">
            {{ enableStatusLabel(row.status) }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <el-button text type="primary" size="small" @click="handleBind(row.code)">{{ $t('design.bind') }}</el-button>
        </template>
      </ResponsiveTable>
      <template #footer>
        <el-button @click="bindDialogVisible = false">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <DesignDetailDrawer ref="designDetailDrawerRef" />
    <ChainDetailDrawer ref="chainDetailDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { designApi, type DesignVO } from '@/api/design'
import type { ChainVO } from '@/api/chain'
import { executorApi, type AppOption } from '@/api/executor'
import CreateDesignDialog from '@/components/CreateDesignDialog.vue'
import ExecutorReadCacheAlert from '@/components/ExecutorReadCacheAlert.vue'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import DesignDetailDrawer from '@/components/DesignDetailDrawer.vue'
import ChainDetailDrawer from '@/components/ChainDetailDrawer.vue'
import { useCurrentApp } from '@/composables/useCurrentApp'
import { consumeExecutorReadCacheMeta } from '@/composables/useExecutorReadCache'
import { useResponsivePagination } from '@/composables/useResponsivePagination'
import { useDictLabel } from '@/composables/useDictLabel'

const { t } = useI18n()
const { dictOptions: enableStatusOptions, labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')
const router = useRouter()
const { currentAppCode, syncFromApps } = useCurrentApp()
const { paginationLayout } = useResponsivePagination()

const designDetailDrawerRef = ref<InstanceType<typeof DesignDetailDrawer> | null>(null)
const chainDetailDrawerRef = ref<InstanceType<typeof ChainDetailDrawer> | null>(null)

const designColumns = computed(() => [
  { prop: 'code', label: t('design.code'), width: 160, showOverflowTooltip: true },
  { prop: 'name', label: t('design.name'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'status', label: t('design.status'), width: 80, align: 'center' as const },
  { prop: 'designer', label: t('design.designer'), width: 100, showOverflowTooltip: true },
  { prop: 'appCode', label: t('design.app'), width: 120, showOverflowTooltip: true },
  { prop: 'boundChainCodes', label: t('design.boundChainCodes'), width: 180, showOverflowTooltip: true },
  { prop: 'description', label: t('design.description'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'updatedBy', label: t('common.updatedBy'), width: 120, showOverflowTooltip: true },
  { prop: 'updatedAt', label: t('design.updatedAt'), width: 160, showOverflowTooltip: true },
])

const bindableChainColumns = computed(() => [
  { prop: 'code', label: t('chains.code'), width: 120, showOverflowTooltip: true },
  { prop: 'name', label: t('chains.name'), minWidth: 150, showOverflowTooltip: true },
  { prop: 'status', label: t('chains.status'), width: 80, align: 'center' as const },
])

const loading = ref(false)
const readCacheStale = ref(false)
const apps = ref<AppOption[]>([])
const designList = ref<DesignVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filter = ref({ keyword: '', status: undefined as number | undefined })

const currentAppName = computed(() => {
  const m = apps.value.find(m => m.appCode === currentAppCode.value)
  return m ? m.appName || m.appCode : ''
})

const stats = computed(() => {
  const enabled = designList.value.filter(c => c.status === 1).length
  const disabled = designList.value.filter(c => c.status === 0).length
  return { enabled, disabled }
})

async function fetchApps() {
  try {
    apps.value = await executorApi.listApps()
    syncFromApps(apps.value)
  } catch { /* ignore */ }
}

async function fetchList() {
  if (!currentAppCode.value) return
  loading.value = true
  try {
    const res: any = consumeExecutorReadCacheMeta(await designApi.list({
      appCode: currentAppCode.value,
      keyword: filter.value.keyword || undefined,
      status: filter.value.status,
      page: page.value,
      size: pageSize.value,
    }), readCacheStale)
    designList.value = res.records
    total.value = res.total
  } finally { loading.value = false }
}

function handleAppChange() { page.value = 1; fetchList() }
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
const editingAppCode = ref<string>('')
const editForm = ref({ code: '', name: '', description: '', designer: '' })
const editRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('design.name') }), trigger: 'blur' }],
}

function openEdit(row: DesignVO) {
  editingCode.value = row.code
  editingAppCode.value = row.appCode || ''
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
      appCode: editingAppCode.value || undefined,
    })
    ElMessage.success(t('common.edit') + '成功')
    editDialogVisible.value = false
    await fetchList()
  } finally { editSubmitting.value = false }
}

// 启停 / 删除
async function handleToggleStatus(row: DesignVO) {
  await designApi.toggleStatus(row.code, row.appCode!)
  ElMessage.success(row.status === 1 ? t('design.disable') + '成功' : t('design.enable') + '成功')
  await fetchList()
}

function handleDelete(row: DesignVO) {
  ElMessageBox.confirm(t('design.deleteConfirm', { name: row.name }), t('design.delete'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
  ).then(async () => {
    await designApi.delete(row.code, row.appCode!)
    ElMessage.success(t('design.delete') + '成功')
    await fetchList()
  }).catch(() => {})
}

// 设计 → 跳转编辑器
function handleDesign(row: DesignVO) {
  router.push({ name: 'DesignEditor', params: { id: row.code }, query: { appCode: row.appCode } })
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
      designApi.getBindings(row.code, row.appCode!),
      designApi.getBindable(row.code, row.appCode!),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
  } catch { /* ignore */ }
}

async function handleBind(chainCode: string) {
  if (!bindTarget.value) return
  try {
    await designApi.bind(bindTarget.value.code, chainCode, bindTarget.value.appCode!)
    ElMessage.success(t('design.bind') + '成功')
    const [bindings, bindable] = await Promise.all([
      designApi.getBindings(bindTarget.value.code, bindTarget.value.appCode!),
      designApi.getBindable(bindTarget.value.code, bindTarget.value.appCode!),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
    await fetchList()
  } catch { /* ignore */ }
}

async function handleUnbind(chainCode: string) {
  if (!bindTarget.value) return
  try {
    await designApi.unbind(bindTarget.value.code, chainCode, bindTarget.value.appCode!)
    ElMessage.success(t('design.unbind') + '成功')
    const [bindings, bindable] = await Promise.all([
      designApi.getBindings(bindTarget.value.code, bindTarget.value.appCode!),
      designApi.getBindable(bindTarget.value.code, bindTarget.value.appCode!),
    ])
    boundChains.value = bindings
    bindableChains.value = bindable
    await fetchList()
  } catch { /* ignore */ }
}

function openDesignDetail(row: DesignVO) {
  if (!row.code || !row.appCode) return
  designDetailDrawerRef.value?.open(row.code, row.appCode)
}

async function openChainDetailFromDesign(row: DesignVO) {
  if (!row.boundChainCodes || !row.appCode) return
  const firstCode = row.boundChainCodes.split(',')[0]?.trim()
  if (firstCode) {
    chainDetailDrawerRef.value?.open(firstCode, row.appCode)
  }
}

onMounted(async () => {
  await fetchApps()
  await fetchList()
})
</script>

<style scoped>
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
