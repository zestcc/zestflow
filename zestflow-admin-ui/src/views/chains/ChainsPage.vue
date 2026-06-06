<template>
  <div class="chains-page">
    <div class="page-header">
      <div class="chain-stats">
        <span class="chain-stats-total">{{ $t('chains.total') }} {{ chainList.length }}</span>
        <el-tag type="danger" size="small">{{ $t('chains.disabled') }} {{ stats.disabled }}</el-tag>
        <el-tag type="info" size="small">{{ $t('chains.notDesigned') }} {{ stats.notDesigned }}</el-tag>
        <el-tag type="warning" size="small">{{ $t('chains.unpublished') }} {{ stats.unpublished }}</el-tag>
        <el-tag type="primary" size="small" effect="dark">{{ $t('chains.publishing') }} {{ stats.publishing }}</el-tag>
        <el-tag type="success" size="small">{{ $t('chains.published') }} {{ stats.published }}</el-tag>
      </div>
      <div class="chain-toolbar">
        <div class="chain-filters">
          <el-select
            v-model="currentAppCode"
            filterable
            class="filter-control filter-app"
            :placeholder="$t('chains.selectApp')"
            @change="handleModuleChange"
          >
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
          <el-input
            v-model="filter.keyword"
            class="filter-control filter-keyword"
            :placeholder="$t('chains.keyword')"
            clearable
            @keyup.enter="handleSearch"
          />
          <el-select
            v-model="filter.status"
            class="filter-control filter-status"
            :placeholder="$t('chains.total')"
            clearable
          >
            <el-option :label="$t('chains.disabled')" :value="0" />
            <el-option :label="$t('chains.notDesigned')" :value="1" />
            <el-option :label="$t('chains.unpublished')" :value="2" />
            <el-option :label="$t('chains.publishing')" :value="3" />
            <el-option :label="$t('chains.published')" :value="4" />
          </el-select>
        </div>
        <div class="chain-filter-actions">
          <el-button type="primary" @click="handleSearch">{{ $t('chains.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('chains.reset') }}</el-button>
          <el-button type="primary" class="chain-create-btn" @click="openCreate">{{ $t('chains.createChain') }}</el-button>
        </div>
      </div>
    </div>

    <ResponsiveTable
      :data="chainList"
      :columns="chainColumns"
      :loading="loading"
      :row-key="'code'"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="200"
    >
      <template #code="{ row }">
        <span class="code-link" @click.stop="openChainDetail(row)">{{ row.code }}</span>
      </template>
      <template #chainKey="{ row }">
        <span v-if="!row.chainKey" style="color:#c0c4cc">-</span>
        <span v-else>{{ row.chainKey }}</span>
      </template>
      <template #name="{ row }">
        {{ row.name || '-' }}
      </template>
      <template #status="{ row }">
        <el-tag :type="statusTagType(row.status)" size="small">
          {{ statusLabel(row.status) }}
        </el-tag>
      </template>
      <template #progress="{ row }">
        <span :style="{ color: row.status === 4 ? '#67c23a' : row.status === 0 ? '#f56c6c' : '#909399' }">
          {{ row.publishedCount || 0 }}/{{ row.totalExecutors || 0 }}
        </span>
      </template>
      <template #designCode="{ row }">
        <span v-if="!row.designCode" style="color:#c0c4cc">-</span>
        <span v-else class="code-link" @click.stop="openDesignDetail(row.designCode, row.appCode)">{{ row.designCode }}</span>
      </template>
      <template #description="{ row }">
        {{ row.description || '-' }}
      </template>
      <template #updatedBy="{ row }">
        {{ row.updatedBy || '-' }}
      </template>
      <template #updatedAt="{ row }">
        {{ row.updatedAt?.replace('T', ' ') || '-' }}
      </template>
      <template #actions="{ row }">
        <el-button v-if="row.status === 2 && row.designCode" text type="primary" size="small" class="action-btn" @click.stop="handlePublish(row)">{{ $t('chains.publish') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click.stop="openDesignDialog(row)">{{ $t('chains.design') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click.stop="openEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button v-if="row.status !== 0" text type="primary" size="small" class="action-btn" @click.stop="handleToggleStatus(row)">{{ $t('chains.disable') }}</el-button>
        <el-button v-else text type="primary" size="small" class="action-btn" @click.stop="handleToggleStatus(row)">{{ $t('chains.enable') }}</el-button>
        <el-button text type="danger" size="small" class="action-btn" @click.stop="handleDelete(row)">{{ $t('common.delete') }}</el-button>
      </template>
    </ResponsiveTable>

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
        <el-form-item :label="$t('chains.app')" prop="appCode">
          <el-select v-model="createForm.appCode" filterable style="width:100%" :placeholder="$t('chains.selectApp')">
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
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

    <!-- 设计管理弹窗 -->
    <el-dialog v-model="designListDialogVisible" title="设计管理" width="800px" :close-on-click-modal="false">
      <div style="margin-bottom:12px;display:flex;align-items:center;justify-content:space-between">
        <span style="font-size:14px;color:#606266">全部 {{ designList.length }}</span>
        <el-button type="primary" size="small" @click="openCreateDesignDialog">新增设计</el-button>
      </div>
      <ResponsiveTable
        :data="designList"
        :columns="designDialogColumns"
        :loading="designLoading"
        row-key="code"
        :show-actions="true"
        :actions-label="'选择'"
        :actions-width="70"
      >
        <template #actions="{ row }">
          <span style="cursor:pointer" @click.stop="toggleDesignSelect(row.code)">
            <el-radio v-model="selectedDesignCode" :label="row.code" @click.stop>&nbsp;</el-radio>
          </span>
        </template>
      </ResponsiveTable>
      <el-empty v-if="!designLoading && designList.length === 0" :image-size="60" description="该应用下暂无设计，请先新建设计" />
      <template #footer>
        <el-button @click="designListDialogVisible = false">取消</el-button>
        <el-button :disabled="!selectedDesignCode" type="primary" :loading="bindingDesign" @click="confirmBindDesign">确定绑定</el-button>
        <el-button v-if="currentChainForDesign?.designCode" :loading="bindingDesign" @click="confirmUnbind">取消绑定</el-button>
      </template>
    </el-dialog>

    <!-- 新增设计弹窗（复用公共组件） -->
    <CreateDesignDialog
      v-model:visible="createDesignDialogVisible"
      :app-options="modules"
      :default-app-code="currentChainForDesign?.appCode || currentAppCode"
      :default-name="currentChainForDesign ? currentChainForDesign.name + t('design.design') : ''"
      :disable-app-select="true"
      append-to-body
      @saved="onDesignCreated"
    >
      <template #footer="{ saving, handleSave }">
        <el-button @click="createDesignDialogVisible = false">取消</el-button>
        <el-button :loading="saving" @click="handleSave">保存</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveThenDesign(handleSave)">去设计</el-button>
      </template>
    </CreateDesignDialog>

    <!-- 链详情抽屉 -->
    <el-drawer
      v-model="chainDrawerVisible"
      :title="$t('chains.chainDetails')"
      :size="chainDrawerSize"
      class="detail-drawer"
      destroy-on-close
      append-to-body
    >
      <template v-if="currentChainDetail">
        <div class="detail-drawer-body">
          <div class="detail-drawer-title">{{ currentChainDetail.name }}</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="编码">
              <el-tag size="small" style="font-family:monospace">{{ currentChainDetail.code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('chains.chainKey')">
              <span v-if="currentChainDetail.chainKey" class="detail-mono-text">{{ currentChainDetail.chainKey }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('chains.appDeclared')">
              <el-tag v-if="currentChainDetail.appDeclared" type="warning" size="small">{{ $t('chains.appDeclaredTag') }}</el-tag>
              <span v-else style="color:#c0c4cc">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(currentChainDetail.status)" size="small">
                {{ statusLabel(currentChainDetail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="应用">
              {{ appNameMap[currentChainDetail.appCode] || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              {{ currentChainDetail.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="关联设计">
              <span v-if="currentChainDetail.designCode" style="color:#409eff;cursor:pointer" @click="openDesignDetail(currentChainDetail.designCode, currentChainDetail.appCode)">{{ currentChainDetail.designCode }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建人">{{ currentChainDetail.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ currentChainDetail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('common.updatedBy')">{{ currentChainDetail.updatedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ currentChainDetail.updatedAt?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </el-drawer>

    <!-- 设计详情抽屉 -->
    <el-drawer
      v-model="designDrawerVisible"
      :title="$t('design.detail')"
      :size="designDrawerSize"
      class="detail-drawer"
      destroy-on-close
      append-to-body
    >
      <template v-if="currentDesignDetail">
        <div class="detail-drawer-body">
          <div class="detail-drawer-header">
            <div class="detail-drawer-title">{{ currentDesignDetail.name }}</div>
            <el-button type="primary" size="small" @click="router.push({ name: 'DesignEditor', params: { id: currentDesignDetail.code }, query: { appCode: currentDesignDetail.appCode } })">
              {{ $t('design.editDesign') }}
            </el-button>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="编码">
              <el-tag size="small" style="font-family:monospace">{{ currentDesignDetail.code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentDesignDetail.status === 1 ? 'success' : 'danger'" size="small">
                {{ currentDesignDetail.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="设计者">
              {{ currentDesignDetail.designer || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="应用">
              {{ appNameMap[currentDesignDetail.appCode] || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              {{ currentDesignDetail.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="绑定链">
              <template v-if="currentDesignDetail.boundChains && currentDesignDetail.boundChains.length > 0">
                <div v-for="c in currentDesignDetail.boundChains" :key="c.id" style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
                  <el-tag size="small" type="info" style="font-family:monospace">{{ c.code }}</el-tag>
                  <span style="font-size:13px;color:#303133">{{ c.name }}</span>
                  <el-tag :type="statusTagType(c.status)" size="small" style="margin-left:auto">
                    {{ statusLabel(c.status) }}
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
  </div>

    <!-- 发布进度弹窗 -->
    <el-dialog v-model="publishDialogVisible" :title="$t('chains.publish') + ' ' + $t('chains.publishStatus')" width="600px" :close-on-click-modal="false" :close-on-press-escape="false">
      <template v-if="publishing">
        <div style="text-align:center;padding:20px 0">
          <el-progress type="circle" :percentage="publishProgressPercent" :status="publishProgressPercent === 100 ? 'success' : undefined" />
          <p style="margin-top:12px;color:#606266">{{ publishProgressText }}</p>
        </div>
      </template>
      <template v-if="publishResults.length > 0">
        <div style="margin-top:12px;font-weight:600;color:#303133;margin-bottom:8px">执行器详情</div>
        <ResponsiveTable
          :data="publishResults"
          :columns="publishResultColumns"
          row-key="url"
        >
          <template #status="{ row }">
            <el-tag :type="row.ok ? 'success' : 'danger'" size="small">{{ row.ok ? '成功' : '失败' }}</el-tag>
          </template>
        </ResponsiveTable>
      </template>
      <template #footer>
        <el-button type="primary" @click="publishDialogVisible = false" :disabled="publishing">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { chainApi, type ChainCreateDTO } from '@/api/chain'
import { executorApi, type AppOption } from '@/api/executor'
import { designApi, type DesignVO } from '@/api/design'
import CreateDesignDialog from '@/components/CreateDesignDialog.vue'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useCurrentApp } from '@/composables/useCurrentApp'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'

const { t } = useI18n()
const router = useRouter()
const { drawerSize: chainDrawerSize } = useResponsiveDrawerSize(480)
const { drawerSize: designDrawerSize } = useResponsiveDrawerSize(520)
const { currentAppCode, syncFromApps } = useCurrentApp()

function statusTagType(status: number): string {
  return ['danger', 'info', 'warning', 'primary', 'success'][status] || 'info'
}
function statusLabel(status: number): string {
  const labels = [t('chains.disabled'), t('chains.notDesigned'), t('chains.unpublished'), t('chains.publishing'), t('chains.published')]
  return labels[status] || '-'
}



const loading = ref(false)
const modules = ref<AppOption[]>([])
const chainList = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const filter = ref({ keyword: '', status: undefined as number | undefined })

const stats = computed(() => {
  const disabled = chainList.value.filter((c: any) => c.status === 0).length
  const notDesigned = chainList.value.filter((c: any) => c.status === 1).length
  const unpublished = chainList.value.filter((c: any) => c.status === 2).length
  const publishing = chainList.value.filter((c: any) => c.status === 3).length
  const published = chainList.value.filter((c: any) => c.status === 4).length
  return { disabled, notDesigned, unpublished, publishing, published }
})

const appNameMap = computed(() => {
  const map: Record<string, string> = {}
  modules.value.forEach(m => { map[m.appCode] = m.appName || m.appCode })
  return map
})

const chainColumns = computed(() => [
  { prop: 'code', label: t('chains.code'), width: 160, showOverflowTooltip: true },
  { prop: 'chainKey', label: t('chains.chainKey'), width: 180, showOverflowTooltip: true },
  { prop: 'name', label: t('chains.name'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 80, align: 'center' as const },
  { prop: 'progress', label: '进度', width: 100, align: 'center' as const },
  { prop: 'designCode', label: '设计编码', width: 160, showOverflowTooltip: true },
  { prop: 'description', label: t('chains.description'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'updatedBy', label: t('common.updatedBy'), width: 120, showOverflowTooltip: true },
  {
    prop: 'updatedAt',
    label: t('chains.updatedAt'),
    width: 160,
    showOverflowTooltip: true,
    formatter: (row: any) => row.updatedAt?.replace('T', ' ') || '-',
  },
])

const designDialogColumns = computed(() => [
  { prop: 'code', label: '设计编码', width: 160, showOverflowTooltip: true },
  { prop: 'name', label: '设计名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'description', label: '描述', minWidth: 140, showOverflowTooltip: true },
])

const publishResultColumns = computed(() => [
  { prop: 'url', label: '执行器地址', showOverflowTooltip: true },
  { prop: 'status', label: '状态', width: 80, align: 'center' as const },
  { prop: 'message', label: '消息', showOverflowTooltip: true },
])

// 链详情抽屉
const chainDrawerVisible = ref(false)
const currentChainDetail = ref<any>(null)
function openChainDetail(row: any) {
  currentChainDetail.value = row
  chainDrawerVisible.value = true
}

// 设计详情抽屉
const designDrawerVisible = ref(false)
const currentDesignDetail = ref<any>(null)
const designDetailLoading = ref(false)
async function openDesignDetail(designCode: string, appCode: string) {
  designDetailLoading.value = true
  designDrawerVisible.value = true
  try {
    currentDesignDetail.value = await designApi.getByCode(designCode, appCode)
  } catch {
    currentDesignDetail.value = null
  } finally {
    designDetailLoading.value = false
  }
}

async function fetchModules() {
  try {
    modules.value = await executorApi.listApps()
    syncFromApps(modules.value)
  } catch { /* ignore */ }
}

async function fetchList() {
  if (!currentAppCode.value) return
  loading.value = true
  try {
    const res = await chainApi.list({
      appCode: currentAppCode.value,
      keyword: filter.value.keyword || undefined,
      status: filter.value.status,
      page: page.value,
      size: pageSize.value,
    })
    chainList.value = (res.records || []).map((row: any) => ({
      ...row,
      appCode: row.appCode || currentAppCode.value,
    }))
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
const createForm = ref({ name: '', description: '', appCode: '' })
const createRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
  appCode: [{ required: true, message: () => t('chains.selectModule'), trigger: 'change' }],
}

function openCreate() {
  createForm.value = { name: '', description: '', appCode: currentAppCode.value }
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    const res = await chainApi.create({
      name: createForm.value.name,
      description: createForm.value.description || undefined,
      appCode: createForm.value.appCode,
    })
    ElMessage.success(t('chains.createChain') + '成功，' + t('chains.code') + '：' + res.code)
    createDialogVisible.value = false
    await fetchList()
  } finally { createSubmitting.value = false }
}

// 编辑
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<any>(null)
const editingCode = ref<string | null>(null)
const editingAppCode = ref<string>('')
const editForm = ref({ name: '', description: '' })
const editRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
}

function openEdit(row: any) {
  editingCode.value = row.code
  editingAppCode.value = row.appCode
  editForm.value = { name: row.name, description: row.description || '' }
  editDialogVisible.value = true
}

async function handleEdit() {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || !editingCode.value) return
  editSubmitting.value = true
  try {
    await chainApi.update(editingCode.value, { name: editForm.value.name, description: editForm.value.description || undefined, appCode: editingAppCode.value })
    ElMessage.success(t('common.edit') + '成功')
    editDialogVisible.value = false
    await fetchList()
  } finally { editSubmitting.value = false }
}

async function handleToggleStatus(row: any) {
  try {
    await chainApi.toggleStatus(row.code, row.appCode)
    ElMessage.success(row.status === 0 ? t('chains.enableSuccess') : t('chains.disableSuccess'))
    fetchList()
  } catch {
    ElMessage.error(t('chains.operationFailed'))
  }
}

// 删除
function handleDelete(row: any) {
  const confirmMsg = row.appDeclared
    ? t('chains.deleteDeclaredConfirm', { name: row.name })
    : t('chains.deleteConfirm', { name: row.name })
  ElMessageBox.confirm(confirmMsg, t('common.delete'),
    { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
  ).then(async () => {
    try {
      await chainApi.delete(row.code, row.appCode)
      ElMessage.success(t('chains.deleteSuccess'))
      await fetchList()
    } catch (e: any) {
      const msg = e?.message || ''
      if (msg.includes('chain_key') || msg.includes('@ZestChain')) {
        ElMessage.error(t('chains.deleteDeclaredBlocked'))
      }
    }
  }).catch(() => {})
}

// 设计管理
const designListDialogVisible = ref(false)
const designList = ref<DesignVO[]>([])
const designLoading = ref(false)
const selectedDesignCode = ref<string | null>(null)
const bindingDesign = ref(false)
const currentChainForDesign = ref<any>(null)

async function openDesignDialog(row: any) {
  const appCode = row.appCode || currentAppCode.value
  currentChainForDesign.value = { ...row, appCode }
  designList.value = []
  selectedDesignCode.value = row.designCode || null
  designListDialogVisible.value = true
  designLoading.value = true
  try {
    const res = await designApi.list({ appCode, page: 1, size: 999 })
    designList.value = res.records || []
  } finally {
    designLoading.value = false
  }
}

function toggleDesignSelect(code: string) {
  if (selectedDesignCode.value === code) {
    selectedDesignCode.value = null
  } else {
    selectedDesignCode.value = code
  }
}

async function confirmBindDesign() {
  if (!selectedDesignCode.value || !currentChainForDesign.value) return

  bindingDesign.value = true
  try {
    await designApi.bind(selectedDesignCode.value, currentChainForDesign.value.code, currentChainForDesign.value.appCode)
    ElMessage.success(t('chains.bindSuccess'))
    designListDialogVisible.value = false
    fetchList()
  } finally {
    bindingDesign.value = false
  }
}

async function confirmUnbind() {
  if (!currentChainForDesign.value?.designCode || !currentChainForDesign.value?.code) return
  bindingDesign.value = true
  try {
    await designApi.unbind(currentChainForDesign.value.designCode, currentChainForDesign.value.code, currentChainForDesign.value.appCode)
    ElMessage.success(t('chains.unbindSuccess'))
    designListDialogVisible.value = false
    fetchList()
  } finally {
    bindingDesign.value = false
  }
}

// 新增设计
const createDesignDialogVisible = ref(false)
const lastCreatedDesign = ref<DesignVO | null>(null)

function openCreateDesignDialog() {
  lastCreatedDesign.value = null
  createDesignDialogVisible.value = true
}

/** "去设计"：若已有设计则直接跳转，否则创建后跳转 */
async function handleSaveThenDesign(handleSave: () => Promise<any>) {
  if (lastCreatedDesign.value) {
    // 已保存过，直接跳转
    goToDesign(lastCreatedDesign.value)
    return
  }
  // 还没有，先保存再跳转
  const design = await handleSave()
  if (design) {
    goToDesign(design)
  }
}

function goToDesign(design: DesignVO) {
  if (!currentChainForDesign.value) return
  const appCode = design.appCode || currentChainForDesign.value.appCode || currentAppCode.value
  designApi.bind(design.code, currentChainForDesign.value.code, appCode).then(() => {
    ElMessage.success(t('chains.createAndBindSuccess'))
    createDesignDialogVisible.value = false
    designListDialogVisible.value = false
    router.push({ name: 'DesignEditor', params: { id: design.code }, query: { appCode } })
  }).catch(() => ElMessage.error(t('chains.operationFailed')))
}

function onDesignCreated(design: DesignVO) {
  lastCreatedDesign.value = design
  ElMessage.success(t('chains.saveSuccess'))
  if (currentChainForDesign.value) {
    designApi.list({ appCode: currentChainForDesign.value.appCode, page: 1, size: 999 }).then(res => {
      designList.value = res.records || []
    })
  }
}

// 发布
const publishDialogVisible = ref(false)
const publishing = ref(false)
const publishProgressPercent = ref(0)
const publishProgressText = ref('')
const publishResults = ref<Array<{ url: string; ok: boolean; message: string }>>([])
const currentPublishingChain = ref<any>(null)

async function handlePublish(row: any) {
  currentPublishingChain.value = row
  publishResults.value = []
  publishProgressPercent.value = 0
  publishProgressText.value = t('chains.publishing') + '...'
  publishing.value = true
  publishDialogVisible.value = true
  try {
    const res = await chainApi.publish(row.code, row.appCode)
    publishResults.value = res.details || []
    const total = res.total || 0
    const success = res.success || 0
    publishProgressPercent.value = total > 0 ? Math.round((success / total) * 100) : 0
    publishProgressText.value = res.message || (res.code === 200 ? t('chains.publishSuccess') : t('chains.publishFailed'))
    if (res.code === 200) {
      ElMessage.success(t('chains.publishSuccess'))
    } else {
      ElMessage.warning(res.message || t('chains.publishFailed'))
    }
    fetchList()
  } catch {
    publishProgressPercent.value = 0
    publishProgressText.value = t('common.requestFailed')
    ElMessage.error(t('common.requestFailed'))
  } finally {
    publishing.value = false
  }
}

onMounted(async () => {
  await fetchModules()
  fetchList()
})
</script>

<style scoped>
.page-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.chain-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.chain-stats-total {
  font-weight: 600;
  color: #409eff;
}

.chain-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.chain-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.filter-control.filter-app {
  width: 200px;
}

.filter-control.filter-keyword {
  width: 200px;
}

.filter-control.filter-status {
  width: 110px;
}

.chain-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn.action-btn { padding: 2px 2px; margin-left: 0; font-size: 12px; }

.detail-drawer-body {
  padding: 0 4px;
}

.detail-drawer-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.detail-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-mono-text {
  font-family: monospace;
  word-break: break-all;
}

@media (max-width: 767px) {
  .chain-stats {
    font-size: 12px;
  }

  .chain-toolbar {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }

  .chain-filters {
    flex-direction: column;
    align-items: stretch;
    width: 100%;
  }

  .filter-control.filter-app,
  .filter-control.filter-keyword,
  .filter-control.filter-status {
    width: 100%;
  }

  .chain-filter-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    width: 100%;
  }

  .chain-create-btn {
    grid-column: 1 / -1;
    width: 100%;
    margin: 0;
  }
}
</style>
