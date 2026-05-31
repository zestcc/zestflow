<template>
  <div>
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('demo.scenes.total') }} {{ total }}</span>
        <el-select
          v-model="currentAppCode"
          filterable
          style="width:200px;margin-left:16px"
          placeholder="选择应用"
          @change="handleAppChange"
        >
          <el-option v-for="m in apps" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
        </el-select>
        <el-input v-model="keyword" :placeholder="$t('demo.scenes.keywordPlaceholder')" clearable style="width:200px;margin-left:16px" @keyup.enter="handleSearch" />
        <el-button type="primary" style="margin-left:8px" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </div>
      <el-button type="primary" @click="openCreateDialog">{{ $t('demo.scenes.create') }}</el-button>
    </div>

    <!-- 表格 -->
    <el-table
        :data="sceneList"
        v-loading="loading"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
      >
        <el-table-column prop="sceneCode" :label="$t('demo.scenes.sceneCode')" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetailDrawer(row)">
              {{ row.sceneCode }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="$t('demo.scenes.name')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="requestPath" :label="$t('demo.scenes.requestPath')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="requestMethod" :label="$t('demo.scenes.requestMethod')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.requestMethod === 'POST' ? 'success' : 'warning'" size="small">
              {{ row.requestMethod }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chainCode" :label="$t('demo.scenes.chainCode')" width="180" show-overflow-tooltip />
        <el-table-column prop="rateLimit" :label="$t('demo.scenes.rateLimit')" width="100" />
        <el-table-column :label="$t('common.actions')" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" class="action-btn" @click="openEditDialog(row)">
              {{ $t('common.edit') }}
            </el-button>
            <el-button type="danger" link size="small" class="action-btn" @click="handleDelete(row)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>

    <!-- 新建/编辑 弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('demo.scenes.edit') : $t('demo.scenes.create')"
      width="800px"
      :close-on-click-modal="false"
      @closed="loadData"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="small">
        <el-form-item :label="$t('demo.scenes.name')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('demo.scenes.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('demo.scenes.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('demo.scenes.descPlaceholder')" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('demo.scenes.requestPath')" prop="requestPath">
              <el-input v-model="form.requestPath" placeholder="/execute" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('demo.scenes.requestMethod')">
              <el-select v-model="form.requestMethod">
                <el-option label="POST" value="POST" />
                <el-option label="GET" value="GET" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('demo.scenes.bodyType')">
              <el-select v-model="form.bodyType">
                <el-option label="JSON" value="JSON" />
                <el-option label="FORM" value="FORM" />
                <el-option label="RAW" value="RAW" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('demo.scenes.chainCode')" prop="chainCode">
          <el-select v-model="form.chainCode" filterable clearable placeholder="搜索选择链" style="width:100%">
            <el-option v-for="c in chainOptions" :key="c.code" :label="c.code + ' - ' + c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('demo.scenes.rateLimit')">
              <el-input-number v-model="form.rateLimit" :min="1" :max="1000" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('demo.scenes.requestHeaders')">
          <el-input v-model="form.requestHeaders" type="textarea" :rows="2" placeholder='{"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item :label="$t('demo.scenes.requestBody')">
          <el-input v-model="form.requestBody" type="textarea" :rows="4" placeholder='{"key":"value"}' />
        </el-form-item>
        <el-form-item :label="$t('demo.scenes.responseExample')">
          <el-input v-model="form.responseExample" type="textarea" :rows="4" placeholder='{"code":200,"data":{}}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情 Drawer -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('demo.scenes.detail')"
      size="500px"
    >
      <template v-if="detailData">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('demo.scenes.sceneCode')">{{ detailData.sceneCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.name')">{{ detailData.name }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.description')">{{ detailData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.requestPath')">{{ detailData.requestPath }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.requestMethod')">{{ detailData.requestMethod }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.chainCode')">{{ detailData.chainCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.rateLimit')">{{ detailData.rateLimit }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.bodyType')">{{ detailData.bodyType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.requestHeaders')">
            <pre class="detail-json">{{ detailData.requestHeaders || '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.requestBody')">
            <pre class="detail-json">{{ formatJson(detailData.requestBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.responseExample')">
            <pre class="detail-json">{{ formatJson(detailData.responseExample) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.createdBy')">{{ detailData.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.createdAt')">{{ detailData.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.updatedBy')">{{ detailData.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('demo.scenes.updatedAt')">{{ detailData.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { executorApi, type AppOption } from '@/api/executor'
import { chainApi, type ChainVO } from '@/api/chain'
import {
  queryScenePage, getSceneById, createScene, updateScene, deleteScene,
  type DemoSceneVO, type DemoSceneCreateDTO, type DemoSceneUpdateDTO,
} from '@/api/demo-scene'

const { t } = useI18n()

const loading = ref(false)
const sceneList = ref<DemoSceneVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const apps = ref<AppOption[]>([])
const currentAppCode = ref('')
const chainOptions = ref<ChainVO[]>([])

// Dialog
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref()

const defaultForm: DemoSceneCreateDTO = {
  name: '',
  description: '',
  requestPath: '/execute',
  requestMethod: 'POST',
  bodyType: 'JSON',
  requestHeaders: '',
  requestBody: '',
  responseExample: '',
  chainCode: '',
  rateLimit: 30,
}

const form = reactive<DemoSceneCreateDTO>({ ...defaultForm })

const rules = {
  name: [{ required: true, message: () => t('demo.scenes.nameRequired'), trigger: 'blur' }],
  requestPath: [{ required: true, message: () => t('demo.scenes.pathRequired'), trigger: 'blur' }],
  chainCode: [{ required: true, message: () => t('demo.scenes.chainCodeRequired'), trigger: 'blur' }],
}

// Detail drawer
const detailVisible = ref(false)
const detailData = ref<DemoSceneVO | null>(null)

async function loadApps() {
  try {
    const res: any = await executorApi.listApps()
    const data = res.data || res
    apps.value = Array.isArray(data) ? data : []
    if (apps.value.length > 0 && !currentAppCode.value) {
      currentAppCode.value = apps.value[0].appCode
    }
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await queryScenePage(keyword.value, currentAppCode.value || undefined, page.value, size.value)
    const data = res.data || res
    sceneList.value = data.records || []
    total.value = data.total || 0
  } catch {
    sceneList.value = []
  } finally {
    loading.value = false
  }
}

async function loadChainOptions() {
  if (!currentAppCode.value) { chainOptions.value = []; return }
  try {
    const res: any = await chainApi.list({ appCode: currentAppCode.value, page: 1, size: 999 })
    const data = res.data || res
    chainOptions.value = data.records || []
  } catch { chainOptions.value = [] }
}
function handleAppChange() { page.value = 1; loadData(); loadChainOptions() }
function handleSearch() { page.value = 1; loadData() }
function handleReset() { keyword.value = ''; page.value = 1; loadData() }

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  Object.assign(form, { ...defaultForm })
  loadChainOptions()
  dialogVisible.value = true
}

function openEditDialog(row: DemoSceneVO) {
  isEditing.value = true
  editingId.value = row.id
  loadChainOptions()
  form.name = row.name
  form.description = row.description || ''
  form.requestPath = row.requestPath
  form.requestMethod = row.requestMethod
  form.bodyType = row.bodyType
  form.requestHeaders = row.requestHeaders || ''
  form.requestBody = row.requestBody || ''
  form.responseExample = row.responseExample || ''
  form.chainCode = row.chainCode
  form.rateLimit = row.rateLimit
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateScene(editingId.value, form as DemoSceneUpdateDTO)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      const res: any = await createScene(form as DemoSceneCreateDTO)
      const vo = res.data || res
      ElMessage.success(t('demo.scenes.createSuccess', { code: vo.sceneCode }))
    }
    dialogVisible.value = false
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: DemoSceneVO) {
  try {
    await ElMessageBox.confirm(t('demo.scenes.deleteConfirm', { name: row.name }), t('common.confirm'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await deleteScene(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // cancelled
  }
}

async function openDetailDrawer(row: DemoSceneVO) {
  detailVisible.value = true
  detailData.value = null
  try {
    const res: any = await getSceneById(row.id)
    detailData.value = res.data || res
  } catch {
    detailData.value = row
  }
}

function formatJson(str: string | null | undefined): string {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

onMounted(() => { loadApps(); loadData() })
</script>

<style scoped>
.page-header { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
.stats-summary { display: flex; align-items: center; font-size: 14px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail-json { margin: 0; font-family: monospace; font-size: 12px; white-space: pre-wrap; max-height: 200px; overflow-y: auto; background: #f5f7fa; padding: 8px; border-radius: 4px; }
.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
