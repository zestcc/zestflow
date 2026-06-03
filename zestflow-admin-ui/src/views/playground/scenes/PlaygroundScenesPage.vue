<template>
  <div>
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('playground.scenes.total') }} {{ total }}</span>
        <el-select
          v-model="currentAppCode"
          filterable
          style="width:200px;margin-left:16px"
          placeholder="选择应用"
          @change="handleAppChange"
        >
          <el-option v-for="m in apps" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
        </el-select>
        <el-input v-model="keyword" :placeholder="$t('playground.scenes.keywordPlaceholder')" clearable style="width:200px;margin-left:16px" @keyup.enter="handleSearch" />
        <el-button type="primary" style="margin-left:8px" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </div>
      <el-button type="primary" @click="openCreateDialog">{{ $t('playground.scenes.create') }}</el-button>
      <el-button @click="openImportDialog">{{ $t('playground.scenes.importFromController') }}</el-button>
    </div>

    <!-- 表格 -->
    <el-table
        :data="sceneList"
        v-loading="loading"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
      >
        <el-table-column prop="sceneCode" :label="$t('playground.scenes.sceneCode')" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span
              v-if="row.sceneCode"
              class="code-link"
              @click.stop="openDetailDrawer(row)"
            >{{ row.sceneCode }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" :label="$t('playground.scenes.name')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="requestPath" :label="$t('playground.scenes.requestPath')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="requestMethod" :label="$t('playground.scenes.requestMethod')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.requestMethod === 'POST' ? 'success' : 'warning'" size="small">
              {{ row.requestMethod }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('playground.scenes.executionChainCode')" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span
              v-if="row.chainCode"
              class="code-link"
              @click.stop="openChainDetail(row.chainCode, row.appCode)"
            >{{ row.chainCode }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="rateLimit" :label="$t('playground.scenes.rateLimit')" width="100" />
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
      :title="isEditing ? $t('playground.scenes.edit') : $t('playground.scenes.create')"
      width="800px"
      :close-on-click-modal="false"
      @closed="loadData"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" size="small">
        <el-form-item :label="$t('playground.scenes.name')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('playground.scenes.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('playground.scenes.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('playground.scenes.descPlaceholder')" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('playground.scenes.requestPath')" prop="requestPath">
              <el-input v-model="form.requestPath" placeholder="/execute" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('playground.scenes.requestMethod')">
              <el-select v-model="form.requestMethod">
                <el-option label="POST" value="POST" />
                <el-option label="GET" value="GET" />
                <el-option label="PUT" value="PUT" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('playground.scenes.bodyType')">
              <el-select v-model="form.bodyType">
                <el-option label="JSON" value="JSON" />
                <el-option label="FORM" value="FORM" />
                <el-option label="RAW" value="RAW" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('playground.scenes.chainCode')" prop="chainCode">
          <el-select v-model="form.chainCode" filterable clearable placeholder="搜索选择链" style="width:100%">
            <el-option v-for="c in chainOptions" :key="c.code" :label="c.code + ' - ' + c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('playground.scenes.rateLimit')">
              <el-input-number v-model="form.rateLimit" :min="1" :max="1000" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('playground.scenes.requestHeaders')">
          <el-input v-model="form.requestHeaders" type="textarea" :rows="2" placeholder='{"Content-Type":"application/json"}' />
        </el-form-item>
        <el-form-item :label="$t('playground.scenes.requestBody')">
          <el-input v-model="form.requestBody" type="textarea" :rows="4" placeholder='{"key":"value"}' />
        </el-form-item>
        <el-form-item :label="$t('playground.scenes.responseExample')">
          <el-input v-model="form.responseExample" type="textarea" :rows="4" placeholder='{"code":200,"data":{}}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 从 Controller 导入弹窗 -->
    <el-dialog
      v-model="importVisible"
      :title="$t('playground.scenes.importDialogTitle')"
      width="900px"
      :close-on-click-modal="false"
    >
      <p style="margin:0 0 12px;color:#909399;font-size:13px">{{ $t('playground.scenes.importDialogDesc') }}</p>
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-select v-model="endpointClassName" clearable :placeholder="$t('playground.scenes.importControllerPlaceholder')" style="width:200px" @change="handleEndpointSearch">
          <el-option v-for="c in endpointClasses" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input
          v-model="importKeyword"
          :placeholder="$t('playground.scenes.importSearchPlaceholder')"
          clearable
          style="width:240px"
          @keyup.enter="handleEndpointSearch"
        />
        <el-button type="primary" @click="handleEndpointSearch">{{ $t('common.search') }}</el-button>
      </div>
      <el-table
        :data="endpointList"
        v-loading="endpointLoading"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
        highlight-current-row
        @current-change="onEndpointSelect"
      >
        <el-table-column prop="className" label="Controller" width="200" show-overflow-tooltip />
        <el-table-column prop="methodName" label="方法" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="openEndpointDetail(row)">
              {{ row.methodName }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="requestMethod" label="Method" width="90">
          <template #default="{ row }">
            <el-tag :type="row.requestMethod === 'POST' ? 'success' : row.requestMethod === 'GET' ? 'primary' : 'warning'" size="small">
              {{ row.requestMethod }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestPath" label="接口路径" min-width="220" show-overflow-tooltip />
        <el-table-column :label="$t('design.colSelected')" width="55" align="center">
          <template #default="{ row }">
            <el-tag v-if="selectedEndpoint?.className === row.className && selectedEndpoint?.methodName === row.methodName && selectedEndpoint?.requestPath === row.requestPath" type="success" size="small">{{ $t('design.selected') }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;display:flex;justify-content:flex-end">
        <el-pagination
          v-model:current-page="endpointPage"
          :page-size="10"
          :total="endpointTotal"
          layout="total, prev, pager, next"
          background
          small
          @current-change="loadEndpoints"
        />
      </div>
      <template #footer>
        <el-button size="small" @click="importVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button size="small" type="primary" @click="handleImport">{{ $t('playground.scenes.importConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 端点详情 Drawer -->
    <el-drawer
      v-model="endpointDetailVisible"
      :title="endpointDetailData?.methodName || '端点详情'"
      size="680px"
    >
      <template v-if="endpointDetailData">
        <el-descriptions :column="1" border size="small" style="margin-bottom:20px">
          <el-descriptions-item label="Controller" :span="1">
            <el-tag size="small" effect="plain">{{ endpointDetailData.className }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Method" :span="1">
            <el-tag :type="endpointDetailData.requestMethod === 'POST' ? 'success' : endpointDetailData.requestMethod === 'GET' ? 'primary' : 'warning'" size="small">
              {{ endpointDetailData.requestMethod }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="接口路径" :span="1">
            <code style="font-size:13px;word-break:break-all">{{ endpointDetailData.requestPath }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="参数" :span="1">
            {{ extractFields(endpointDetailData) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="endpointDetailData.requestHeaders" label="请求头" :span="1">
            <el-tag size="small" type="info" style="margin:1px 4px 1px 0" v-for="h in endpointDetailData.requestHeaders.split(',')" :key="h">{{ h.trim() }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="endpointDetailData.hasRequestBody" style="margin-bottom:16px">
          <div style="font-weight:600;font-size:14px;margin-bottom:8px;color:#303133">
            请求体 · <el-tag size="small" effect="plain" type="info">{{ endpointDetailData.requestBodyType }}</el-tag>
          </div>
          <pre class="endpoint-json">{{ formatJson(endpointDetailData.requestBodyTemplate) }}</pre>
        </div>

        <div v-if="endpointDetailData.responseBodyTemplate">
          <div style="font-weight:600;font-size:14px;margin-bottom:8px;color:#303133">
            响应体 · <el-tag size="small" effect="plain" type="info">{{ endpointDetailData.responseBodyType }}</el-tag>
          </div>
          <pre class="endpoint-json">{{ formatJson(endpointDetailData.responseBodyTemplate) }}</pre>
        </div>
      </template>
    </el-drawer>

    <!-- 详情 Drawer -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('playground.scenes.detail')"
      size="500px"
    >
      <template v-if="detailData">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('playground.scenes.sceneCode')">{{ detailData.sceneCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.name')">{{ detailData.name }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.description')">{{ detailData.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.requestPath')">{{ detailData.requestPath }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.requestMethod')">{{ detailData.requestMethod }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.executionChainCode')">
            <span
              v-if="detailData.chainCode"
              class="code-link"
              @click="openChainDetail(detailData.chainCode)"
            >{{ detailData.chainCode }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.rateLimit')">{{ detailData.rateLimit }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.bodyType')">{{ detailData.bodyType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.requestHeaders')">
            <pre class="detail-json">{{ detailData.requestHeaders || '-' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.requestBody')">
            <pre class="detail-json">{{ formatJson(detailData.requestBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.responseExample')">
            <pre class="detail-json">{{ formatJson(detailData.responseExample) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.createdBy')">{{ detailData.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.createdAt')">{{ detailData.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.updatedBy')">{{ detailData.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('playground.scenes.updatedAt')">{{ detailData.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <ChainDetailDrawer ref="chainDetailDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import ChainDetailDrawer from '@/components/ChainDetailDrawer.vue'
import { executorApi, type AppOption } from '@/api/executor'
import { chainApi, type ChainVO } from '@/api/chain'
import {
  queryPlaygroundScenePage, getPlaygroundSceneById, getPlaygroundSceneByCode, createPlaygroundScene, updatePlaygroundScene, deletePlaygroundScene,
  getAvailableEndpoints, getEndpointClasses,
  type PlaygroundSceneVO, type PlaygroundSceneCreateDTO, type PlaygroundSceneUpdateDTO,
  type AvailableEndpoint,
} from '@/api/playground-scene'

const { t } = useI18n()
const route = useRoute()

const chainDetailDrawerRef = ref<InstanceType<typeof ChainDetailDrawer> | null>(null)

const loading = ref(false)
const sceneList = ref<PlaygroundSceneVO[]>([])
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

const defaultForm: PlaygroundSceneCreateDTO = {
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
  appCode: '',
}

const form = reactive<PlaygroundSceneCreateDTO>({ ...defaultForm })

const rules = {
  name: [{ required: true, message: () => t('playground.scenes.nameRequired'), trigger: 'blur' }],
  requestPath: [{ required: true, message: () => t('playground.scenes.pathRequired'), trigger: 'blur' }],
  chainCode: [{ required: true, message: () => t('playground.scenes.chainCodeRequired'), trigger: 'blur' }],
}

// Import from Controller
const importVisible = ref(false)
const importKeyword = ref('')
const endpointLoading = ref(false)
const endpointList = ref<AvailableEndpoint[]>([])
const endpointPage = ref(1)
const endpointTotal = ref(0)
const endpointClasses = ref<string[]>([])
const endpointClassName = ref('')
const selectedEndpoint = ref<AvailableEndpoint | null>(null)
const endpointDetailVisible = ref(false)
const endpointDetailData = ref<AvailableEndpoint | null>(null)

function openEndpointDetail(row: AvailableEndpoint) {
  endpointDetailData.value = row
  endpointDetailVisible.value = true
}

async function loadEndpoints() {
  if (!currentAppCode.value) { endpointList.value = []; endpointTotal.value = 0; return }
  endpointLoading.value = true
  try {
    const res: any = await getAvailableEndpoints(currentAppCode.value, importKeyword.value || undefined, endpointClassName.value || undefined, endpointPage.value, 10)
    const data = res.data || res
    endpointList.value = data.records || []
    endpointTotal.value = data.total || 0
  } catch {
    endpointList.value = []
    endpointTotal.value = 0
  } finally {
    endpointLoading.value = false
  }
}

async function loadEndpointClasses() {
  if (!currentAppCode.value) { endpointClasses.value = []; return }
  try {
    const res: any = await getEndpointClasses(currentAppCode.value)
    const data = res.data || res
    endpointClasses.value = Array.isArray(data) ? data : []
  } catch {
    endpointClasses.value = []
  }
}

function handleEndpointSearch() {
  endpointPage.value = 1
  loadEndpoints()
}

function onEndpointSelect(row: AvailableEndpoint | null) {
  selectedEndpoint.value = row
}

function openImportDialog() {
  selectedEndpoint.value = null
  importKeyword.value = ''
  endpointClassName.value = ''
  endpointPage.value = 1
  endpointTotal.value = 0
  importVisible.value = true
  loadEndpoints()
  loadEndpointClasses()
}

function handleImport() {
  if (!selectedEndpoint.value) {
    ElMessage.warning(t('playground.scenes.noEndpointSelected'))
    return
  }
  const ep = selectedEndpoint.value
  // 填充创建表单
  form.name = ep.className + ' - ' + ep.methodName
  form.requestPath = ep.requestPath
  form.requestMethod = ep.requestMethod === 'ALL' ? 'POST' : ep.requestMethod
  form.description = ''
  form.chainCode = ''
  form.rateLimit = 30
  form.requestHeaders = ''
  form.requestBody = ep.hasRequestBody && ep.requestBodyTemplate
    ? ep.requestBodyTemplate
    : ''
  form.responseExample = ep.responseBodyTemplate || ''
  form.bodyType = ep.hasRequestBody ? 'JSON' : 'FORM'
  form.appCode = currentAppCode.value
  // 关闭导入弹窗，打开创建弹窗
  importVisible.value = false
  isEditing.value = false
  editingId.value = null
  loadChainOptions()
  dialogVisible.value = true
}

// Detail drawer
const detailVisible = ref(false)
const detailData = ref<PlaygroundSceneVO | null>(null)

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
    const res: any = await queryPlaygroundScenePage(keyword.value, currentAppCode.value || undefined, page.value, size.value)
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
  Object.assign(form, { ...defaultForm, appCode: currentAppCode.value })
  loadChainOptions()
  dialogVisible.value = true
}

function openEditDialog(row: PlaygroundSceneVO) {
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
      await updatePlaygroundScene(editingId.value, form as PlaygroundSceneUpdateDTO)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      const res: any = await createPlaygroundScene(form as PlaygroundSceneCreateDTO)
      const vo = res.data || res
      ElMessage.success(t('playground.scenes.createSuccess', { code: vo.sceneCode }))
    }
    dialogVisible.value = false
  } catch {
    ElMessage.error(t('common.operationFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: PlaygroundSceneVO) {
  try {
    await ElMessageBox.confirm(t('playground.scenes.deleteConfirm', { name: row.name }), t('common.confirm'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    await deletePlaygroundScene(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {
    // cancelled
  }
}

async function openDetailDrawer(row: PlaygroundSceneVO) {
  detailVisible.value = true
  detailData.value = null
  try {
    const res: any = await getPlaygroundSceneById(row.id)
    detailData.value = res.data || res
  } catch {
    detailData.value = row
  }
}

async function openDetailBySceneCode(sceneCode: string) {
  detailVisible.value = true
  detailData.value = null
  try {
    const res: any = await getPlaygroundSceneByCode(sceneCode)
    detailData.value = res.data || res
  } catch {
    detailVisible.value = false
  }
}

function openChainDetail(chainCode: string, appCode?: string) {
  const resolvedAppCode = appCode || detailData.value?.appCode || currentAppCode.value
  if (!resolvedAppCode) return
  chainDetailDrawerRef.value?.open(chainCode, resolvedAppCode)
}

function formatJson(str: string | null | undefined): string {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

/** 从端点信息中提取参数字段列表 */
function extractFields(row: AvailableEndpoint): string {
  if (row.requestBodyTemplate && row.requestBodyTemplate !== '{}') {
    return extractFieldsFromBody(row.requestBodyTemplate)
  }
  return row.parameters?.join(', ') || '-'
}

/** 从请求体 JSON 中提取字段名列表 */
function extractFieldsFromBody(body: string | null | undefined): string {
  if (!body || body === '{}' || body === '') return '-'
  const names: string[] = []
  const re = /"(\w+)":/g
  let m: RegExpExecArray | null
  while ((m = re.exec(body)) !== null) {
    names.push(m[1])
  }
  return names.length > 0 ? names.join(', ') : '-'
}

onMounted(async () => {
  await loadApps()
  const appCodeFromQuery = route.query.appCode as string | undefined
  if (appCodeFromQuery) {
    currentAppCode.value = appCodeFromQuery
  }
  await loadData()
  const sceneCodeFromQuery = route.query.sceneCode as string | undefined
  if (sceneCodeFromQuery) {
    await openDetailBySceneCode(sceneCodeFromQuery)
  }
})
</script>

<style scoped>
.page-header { margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
.stats-summary { display: flex; align-items: center; font-size: 14px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail-json { margin: 0; font-family: monospace; font-size: 12px; white-space: pre-wrap; max-height: 200px; overflow-y: auto; background: #f5f7fa; padding: 8px; border-radius: 4px; }
.action-btn { padding: 2px 4px; margin-left: 0; }
.endpoint-json { margin: 0; font-family: 'SF Mono', 'Cascadia Code', monospace; font-size: 12px; line-height: 1.6; white-space: pre-wrap; background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 6px; max-height: 360px; overflow-y: auto; }
</style>
