<template>
  <div class="playground-container" :class="{ 'playground-container--mobile': isMobileView, 'playground-container--tablet': isTabletView }">
    <!-- 顶栏 -->
    <div class="pg-header">
      <div class="pg-header-left">
        <el-icon :size="22" color="#409eff"><Connection /></el-icon>
        <h2>{{ $t('playground.playground.title') }}</h2>
      </div>
      <el-tag type="warning" size="small" effect="plain">{{ $t('playground.playground.modeTag') }}</el-tag>
    </div>

    <div v-if="isMobileView" class="pg-mobile-tabs">
      <el-button
        size="small"
        :type="mobilePanel === 'history' ? 'primary' : 'default'"
        @click="mobilePanel = 'history'"
      >{{ $t('playground.playground.history') }}</el-button>
      <el-button
        size="small"
        :type="mobilePanel === 'workspace' ? 'primary' : 'default'"
        @click="mobilePanel = 'workspace'"
      >{{ $t('playground.playground.debugPanel') }}</el-button>
    </div>

    <!-- 主内容区 -->
    <div class="pg-body">
      <!-- 左栏：历史记录 -->
      <div v-show="!isMobileView || mobilePanel === 'history'" class="pg-history">
        <div class="pg-panel-title">
          <span>{{ $t('playground.playground.history') }}</span>
          <el-tag size="small" type="info">{{ totalHistory }}</el-tag>
        </div>
        <div class="pg-history-list" v-loading="historyLoading">
          <div
            v-for="item in historyList"
            :key="item.id"
            class="pg-history-item"
            :class="{ active: selectedHistoryId === item.id }"
            @click="loadHistoryDetail(item)"
          >
            <div class="history-item-top">
              <el-tag :type="item.status === 1 ? 'success' : 'danger'" size="small" class="history-status">
                {{ item.status === 1 ? '✓' : '✗' }}
              </el-tag>
              <span class="history-scene">{{ item.sceneName }}</span>
              <span class="history-time">{{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="history-item-bottom">
              <span class="history-chain">{{ item.chainCode }}</span>
              <span class="history-cost">{{ item.costMs }}ms</span>
            </div>
          </div>
          <el-empty v-if="!historyLoading && historyList.length === 0" :description="$t('playground.playground.noHistory')" />
        </div>
        <div class="pg-history-pager" v-if="totalHistory > size">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="size"
            :total="totalHistory"
            :pager-count="5"
            size="small"
            layout="prev,pager,next"
            background
            hide-on-single-page
            @current-change="loadHistory"
          />
        </div>
      </div>

      <!-- 右栏：请求 + 响应 -->
      <div v-show="!isMobileView || mobilePanel === 'workspace'" class="pg-workspace">
        <!-- 请求区 -->
        <div class="pg-section">
          <div class="pg-panel-title">{{ $t('playground.playground.request') }}</div>

          <!-- 场景选择 + URL -->
          <div class="pg-url-bar">
            <el-select
              v-model="currentAppCode"
              filterable
              class="pg-app-select"
              placeholder="选择应用"
              @change="handleAppChange"
            >
              <el-option v-for="m in apps" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
            </el-select>
            <el-select
              v-model="selectedSceneCode"
              :placeholder="$t('playground.playground.selectScene')"
              class="pg-scene-select"
              filterable
              @change="onSceneChange"
            >
              <el-option
                v-for="s in scenes"
                :key="s.sceneCode"
                :label="s.name"
                :value="s.sceneCode"
              >
                <div class="scene-option">
                  <span>{{ s.name }}</span>
                  <span class="scene-option-desc">{{ s.description }}</span>
                </div>
              </el-option>
            </el-select>
            <el-tag :type="methodTagType" size="small" class="pg-method-tag">
              {{ sceneInfo?.requestMethod || 'POST' }}
            </el-tag>
            <div class="pg-url-display">{{ requestPathDisplay }}</div>
          </div>

          <!-- 场景描述 -->
          <div class="pg-scene-desc" v-if="sceneInfo">
            <el-icon><InfoFilled /></el-icon>
            <span class="pg-desc-text">{{ sceneInfo.description }}</span>
            <el-tag size="small" type="info" class="pg-chain-tag">
              {{ $t('playground.playground.chainCode') }}: {{ sceneInfo.chainCode }}
            </el-tag>
          </div>

          <!-- 自定义请求头 -->
          <div class="pg-section-block">
            <div class="pg-block-header" @click="headersExpanded = !headersExpanded">
              <span>{{ $t('playground.playground.headers') }}</span>
              <el-icon :class="{ rotated: headersExpanded }"><ArrowRight /></el-icon>
            </div>
            <div v-show="headersExpanded" class="pg-block-body">
              <div v-for="(h, i) in customHeaders" :key="i" class="pg-header-row">
                <el-input v-model="h.key" :placeholder="$t('playground.playground.headerKey')" size="small" class="pg-header-key" />
                <el-input v-model="h.value" :placeholder="$t('playground.playground.headerValue')" size="small" class="pg-header-value" />
                <el-button type="danger" :icon="Delete" size="small" circle @click="customHeaders.splice(i, 1)" />
              </div>
              <el-button size="small" :icon="Plus" @click="addHeader" class="pg-add-header-btn">
                {{ $t('playground.playground.addHeader') }}
              </el-button>
            </div>
          </div>

          <!-- 请求体 -->
          <div class="pg-section-block">
            <div class="pg-block-header">
              <span>{{ $t('playground.playground.body') }}</span>
              <el-radio-group v-model="bodyType" size="small" class="pg-body-type">
                <el-radio-button value="json">JSON</el-radio-button>
                <el-radio-button value="form">Form</el-radio-button>
                <el-radio-button value="raw">Raw</el-radio-button>
              </el-radio-group>
            </div>
            <div class="pg-block-body pg-body-editor">
              <!-- JSON 编辑器 -->
              <div v-show="bodyType === 'json'">
                <div class="pg-editor-toolbar">
                  <el-button size="small" text @click="formatJson">{{ $t('playground.playground.format') }}</el-button>
                  <el-button size="small" text @click="flattenJson">{{ $t('playground.playground.flatten') }}</el-button>
                </div>
                <textarea
                  v-model="requestBody"
                  class="pg-code-editor"
                  :placeholder="$t('playground.playground.bodyPlaceholder')"
                  spellcheck="false"
                  rows="8"
                />
              </div>
              <!-- Form 编辑器 -->
              <div v-show="bodyType === 'form'">
                <div v-for="(f, i) in formFields" :key="i" class="pg-form-row">
                  <el-input v-model="f.key" :placeholder="$t('playground.playground.formKey')" size="small" class="pg-form-key" />
                  <el-input v-model="f.value" :placeholder="$t('playground.playground.formValue')" size="small" class="pg-form-value" />
                  <el-button type="danger" :icon="Delete" size="small" circle @click="formFields.splice(i, 1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="formFields.push({ key: '', value: '' })">
                  {{ $t('playground.playground.addField') }}
                </el-button>
              </div>
              <!-- Raw 编辑器 -->
              <div v-show="bodyType === 'raw'">
                <textarea
                  v-model="requestRawBody"
                  class="pg-code-editor"
                  :placeholder="$t('playground.playground.rawPlaceholder')"
                  spellcheck="false"
                  rows="8"
                />
              </div>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="pg-actions">
            <el-button
              type="primary"
              :icon="VideoPlay"
              :size="isMobileView ? 'default' : 'large'"
              :loading="executing"
              :disabled="!selectedSceneCode"
              class="pg-action-btn"
              @click="handleExecute"
            >
              {{ $t('playground.playground.execute') }}
            </el-button>
            <el-button
              :icon="Refresh"
              :size="isMobileView ? 'default' : 'large'"
              :disabled="!selectedHistoryId"
              class="pg-action-btn"
              @click="loadHistoryToForm"
            >
              {{ $t('playground.playground.retry') }}
            </el-button>
          </div>
        </div>

        <!-- 响应区 -->
        <div class="pg-section pg-response-section">
          <div class="pg-panel-title">{{ $t('playground.playground.response') }}</div>

          <!-- 响应摘要 -->
          <div class="pg-response-summary" v-if="lastResult">
            <el-tag :type="lastResult.status === 1 ? 'success' : 'danger'" size="default" class="pg-response-status">
              {{ lastResult.status === 1 ? 'Success' : 'Failed' }}
            </el-tag>
            <span class="pg-response-stat">
              <el-icon><Timer /></el-icon>
              {{ lastResult.costMs }}ms
            </span>
            <span class="pg-response-stat" v-if="lastResult.errorMsg">
              <el-icon><WarningFilled /></el-icon>
              {{ lastResult.errorMsg }}
            </span>
          </div>
          <div class="pg-response-empty" v-else>
            <el-empty :description="$t('playground.playground.noResponse')" />
          </div>

          <!-- 响应体 -->
          <div class="pg-block-body" v-if="lastResult">
            <pre class="pg-response-body"><code>{{ formatResponseBody }}</code></pre>
            <div class="pg-response-actions">
              <el-button size="small" :icon="Link" @click="goToLogs()" :disabled="!currentExecutionId">
                {{ $t('playground.playground.viewLogs') }}
              </el-button>
              <el-button size="small" :icon="CopyDocument" @click="copyResponse">
                {{ $t('playground.playground.copy') }}
              </el-button>
            </div>
          </div>

          <!-- 历史明细详情 -->
          <div v-if="historyDetail" class="pg-history-detail">
            <el-divider />
            <div class="pg-panel-title">{{ $t('playground.playground.historyDetail') }}</div>
            <div class="pg-detail-grid">
              <div class="pg-detail-item">
                <label>{{ $t('playground.playground.scene') }}:</label>
                <span>{{ historyDetail.sceneName }}</span>
              </div>
              <div class="pg-detail-item">
                <label>{{ $t('playground.playground.chainCode') }}:</label>
                <span>{{ historyDetail.chainCode }}</span>
              </div>
              <div class="pg-detail-item">
                <label>{{ $t('playground.playground.instanceId') }}:</label>
                <el-button size="small" link :disabled="!historyDetail.instanceId" @click="goToLogs(historyDetail.instanceId)">
                  {{ historyDetail.instanceId || '-' }}
                </el-button>
              </div>
              <div class="pg-detail-item" v-if="historyDetail.requestHeaders && historyDetail.requestHeaders !== 'null'">
                <label>{{ $t('playground.playground.headers') }}:</label>
                <pre class="pg-detail-json">{{ prettyPrintJson(historyDetail.requestHeaders) }}</pre>
              </div>
              <div class="pg-detail-item" v-if="historyDetail.requestBody">
                <label>{{ $t('playground.playground.requestBody') }}:</label>
                <pre class="pg-detail-json">{{ prettyPrintJson(historyDetail.requestBody) }}</pre>
              </div>
              <div class="pg-detail-item" v-if="historyDetail.responseBody">
                <label>{{ $t('playground.playground.responseBody') }}:</label>
                <pre class="pg-detail-json">{{ prettyPrintJson(historyDetail.responseBody) }}</pre>
              </div>
            </div>
            <el-button size="small" type="primary" @click="loadHistoryToForm">
              {{ $t('playground.playground.loadToForm') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Connection, VideoPlay, Plus, Delete, ArrowRight, InfoFilled,
  Timer, WarningFilled, Link, CopyDocument, Refresh,
} from '@element-plus/icons-vue'
import { listAllPlaygroundScenes, getPlaygroundSceneByCode, type PlaygroundSceneVO } from '@/api/playground-scene'
import {
  executePlaygroundScene, queryPlaygroundHistory, getPlaygroundHistoryDetail,
  type PlaygroundExecuteResult, type PlaygroundRecordVO,
} from '@/api/playground'
import { executorApi, type AppOption } from '@/api/executor'
import { goToLogDetail } from '@/utils/zestflow-nav'
import { useCurrentApp } from '@/composables/useCurrentApp'

const { t } = useI18n()
const router = useRouter()
const { currentAppCode, syncFromApps } = useCurrentApp()

const TABLET_BREAKPOINT = 768
const COMPACT_BREAKPOINT = 1024
const isMobileView = ref(false)
const isTabletView = ref(false)
const mobilePanel = ref<'history' | 'workspace'>('workspace')

function checkViewport() {
  const width = window.innerWidth
  isMobileView.value = width < TABLET_BREAKPOINT
  isTabletView.value = width >= TABLET_BREAKPOINT && width < COMPACT_BREAKPOINT
  if (!isMobileView.value) {
    mobilePanel.value = 'workspace'
  }
}

function showWorkspaceOnMobile() {
  if (isMobileView.value) {
    mobilePanel.value = 'workspace'
  }
}

// === 场景 ===
const scenes = ref<PlaygroundSceneVO[]>([])
const selectedSceneCode = ref('')
const sceneInfo = ref<PlaygroundSceneVO | null>(null)

const methodTagType = computed(() => {
  const m = sceneInfo.value?.requestMethod
  if (m === 'POST') return 'success'
  if (m === 'GET') return 'primary'
  if (m === 'PUT') return 'warning'
  if (m === 'DELETE') return 'danger'
  return 'info'
})

// === 应用 ===
const apps = ref<AppOption[]>([])

async function loadApps() {
  try {
    const res: any = await executorApi.listApps()
    const data = res.data || res
    apps.value = Array.isArray(data) ? data : []
    syncFromApps(apps.value)
  } catch { /* ignore */ }
}

function handleAppChange() {
  loadScenes()
  loadHistory()
}

const requestPathDisplay = computed(() => {
  const path = sceneInfo.value?.requestPath || ''
  if (!selectedSceneCode.value) return path
  const invoke = `/api/playground/execute/${selectedSceneCode.value}`
  if (!path || path === '/execute') return invoke
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${invoke}  →  ${path}`
})

// === 请求体 ===
const bodyType = ref<'json' | 'form' | 'raw'>('json')
const requestBody = ref('{\n  \n}')
const requestRawBody = ref('')
const formFields = ref<{ key: string; value: string }[]>([])

// === 自定义请求头 ===
const headersExpanded = ref(false)
const customHeaders = ref<{ key: string; value: string }[]>([])

function addHeader() {
  customHeaders.value.push({ key: '', value: '' })
}

// === 执行 ===
const executing = ref(false)
const lastResult = ref<PlaygroundExecuteResult | null>(null)
const executeError = ref('')

function resolveExecutionId(result: PlaygroundExecuteResult | null | undefined): string {
  if (!result) return ''
  if (result.instanceId?.trim()) return result.instanceId.trim()
  const nested = result.result
  if (!nested || typeof nested !== 'object') return ''
  const root = nested as Record<string, unknown>
  for (const key of ['instanceId', 'executionId', 'orderId']) {
    const val = root[key]
    if (typeof val === 'string' && val.trim()) return val.trim()
  }
  const data = root.data
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const dataObj = data as Record<string, unknown>
    for (const key of ['instanceId', 'executionId', 'orderId']) {
      const val = dataObj[key]
      if (typeof val === 'string' && val.trim()) return val.trim()
    }
  }
  return ''
}

const currentExecutionId = computed(() => resolveExecutionId(lastResult.value))

function notifyExecuteResult(result: PlaygroundExecuteResult) {
  const executionId = resolveExecutionId(result)
  const costMs = result.costMs ?? 0
  if (result.status === 1) {
    ElNotification.success({
      title: t('playground.playground.executeSuccessTitle'),
      message: executionId
        ? t('playground.playground.executeSuccessWithLog', { costMs, executionId })
        : t('playground.playground.executeSuccessTip', { costMs }),
      duration: 5000,
    })
  } else {
    ElNotification.warning({
      title: t('playground.playground.executeFailTitle'),
      message: result.errorMsg
        ? t('playground.playground.executeFailTip', { errorMsg: result.errorMsg })
        : t('playground.playground.executeFailGeneric'),
      duration: 6000,
    })
  }
}

async function handleExecute() {
  if (!selectedSceneCode.value) return
  executing.value = true
  executeError.value = ''
  lastResult.value = null

  try {
    let params: Record<string, any>
    if (bodyType.value === 'json') {
      try {
        params = JSON.parse(requestBody.value)
      } catch {
        ElMessage.warning(t('playground.playground.invalidJson'))
        executing.value = false
        return
      }
    } else if (bodyType.value === 'form') {
      params = {}
      for (const f of formFields.value) {
        if (f.key) params[f.key] = f.value
      }
    } else {
      params = { raw: requestRawBody.value }
    }

    const res: any = await executePlaygroundScene(selectedSceneCode.value, params)
    lastResult.value = res
    notifyExecuteResult(res)
    await loadHistory()
    showWorkspaceOnMobile()
  } catch (e: any) {
    executeError.value = e.message || t('common.networkError')
    ElMessage.error(executeError.value)
  } finally {
    executing.value = false
  }
}

function formatJson() {
  try {
    const obj = JSON.parse(requestBody.value)
    requestBody.value = JSON.stringify(obj, null, 2)
  } catch {
    ElMessage.warning(t('playground.playground.invalidJson'))
  }
}

function flattenJson() {
  try {
    const obj = JSON.parse(requestBody.value)
    requestBody.value = JSON.stringify(obj)
  } catch {
    ElMessage.warning(t('playground.playground.invalidJson'))
  }
}

const formatResponseBody = computed(() => {
  if (!lastResult.value) return ''
  const payload = lastResult.value.result !== undefined && lastResult.value.result !== null
    ? lastResult.value.result
    : lastResult.value
  if (typeof payload === 'string') return payload
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
})

function copyResponse() {
  navigator.clipboard.writeText(formatResponseBody.value)
  ElMessage.success(t('playground.playground.copied'))
}

function goToLogs(instanceId?: string | null) {
  const id = instanceId?.trim() || currentExecutionId.value
  if (!id) {
    ElMessage.warning(t('playground.playground.noExecutionId'))
    return
  }
  goToLogDetail(router, id, currentAppCode.value)
}

// === 场景切换 ===
async function onSceneChange(sceneCode: string) {
  sceneInfo.value = null
  try {
    const res: any = await getPlaygroundSceneByCode(sceneCode)
    sceneInfo.value = res.data || res
  } catch {
    const s = scenes.value.find(x => x.sceneCode === sceneCode)
    sceneInfo.value = s || null
  }

  // Auto-fill from scene template
  if (sceneInfo.value?.requestBody) {
    requestBody.value = sceneInfo.value.requestBody
  } else {
    requestBody.value = '{\n  \n}'
  }

  // Parse headers from scene template
  customHeaders.value = []
  if (sceneInfo.value?.requestHeaders) {
    try {
      const hdrs = JSON.parse(sceneInfo.value.requestHeaders)
      for (const [k, v] of Object.entries(hdrs)) {
        customHeaders.value.push({ key: k, value: String(v) })
      }
    } catch { /* ignore */ }
  }

  formFields.value = []
  const bt = sceneInfo.value?.bodyType || 'JSON'

  // Format JSON body for editing
  if (bt === 'JSON' && requestBody.value) {
    try {
      const obj = JSON.parse(requestBody.value)
      requestBody.value = JSON.stringify(obj, null, 2)
    } catch { /* ignore */ }
  }

  bodyType.value = bt === 'FORM' ? 'form' : bt === 'RAW' ? 'raw' : 'json'
  lastResult.value = null
}

// === 历史 ===
const historyList = ref<PlaygroundRecordVO[]>([])
const totalHistory = ref(0)
const currentPage = ref(1)
const size = ref(20)
const historyLoading = ref(false)
const selectedHistoryId = ref<number | null>(null)
const historyDetail = ref<PlaygroundRecordVO | null>(null)

async function loadHistory() {
  historyLoading.value = true
  try {
    const res: any = await queryPlaygroundHistory({
      page: currentPage.value,
      size: size.value,
      appCode: currentAppCode.value || undefined,
    })
    const data = res.data || res
    historyList.value = data.records || []
    totalHistory.value = data.total || 0
  } catch {
    // silent
  } finally {
    historyLoading.value = false
  }
}

async function loadScenes() {
  try {
    const data = await listAllPlaygroundScenes(currentAppCode.value || undefined)
    scenes.value = Array.isArray(data) ? data : []
    if (scenes.value.length > 0) {
      selectedSceneCode.value = scenes.value[0].sceneCode
      await onSceneChange(selectedSceneCode.value)
    } else {
      selectedSceneCode.value = ''
      sceneInfo.value = null
    }
  } catch {
    ElMessage.error(t('playground.playground.loadFailed'))
  }
}

async function loadHistoryDetail(item: PlaygroundRecordVO) {
  selectedHistoryId.value = item.id
  try {
    const res: any = await getPlaygroundHistoryDetail(item.id)
    historyDetail.value = res.data || res
  } catch {
    historyDetail.value = item
  }
  showWorkspaceOnMobile()
}

function loadHistoryToForm() {
  if (!historyDetail.value) return

  // Load scene
  if (historyDetail.value.sceneCode) {
    selectedSceneCode.value = historyDetail.value.sceneCode
    onSceneChange(historyDetail.value.sceneCode)
  }

  // Load request body
  if (historyDetail.value.requestBody) {
    requestBody.value = historyDetail.value.requestBody
    if (bodyType.value === 'json') {
      try {
        const obj = JSON.parse(requestBody.value)
        requestBody.value = JSON.stringify(obj, null, 2)
      } catch { /* ignore */ }
    }
  }

  // Load headers
  customHeaders.value = []
  if (historyDetail.value.requestHeaders) {
    try {
      const hdrs = JSON.parse(historyDetail.value.requestHeaders)
      for (const [k, v] of Object.entries(hdrs)) {
        customHeaders.value.push({ key: k, value: String(v) })
      }
    } catch { /* ignore */ }
  }

  // Load body type
  if (historyDetail.value.bodyType) {
    const bt = historyDetail.value.bodyType
    bodyType.value = bt === 'FORM' ? 'form' : bt === 'RAW' ? 'raw' : 'json'
  }

  ElMessage.success(t('playground.playground.loadedToForm'))
  showWorkspaceOnMobile()
}

// === 工具 ===
function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function prettyPrintJson(str: string | null | undefined): string {
  if (!str || str === 'null') return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

// === 初始化 ===
onMounted(async () => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  await loadApps()
  await loadScenes()
  await loadHistory()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
})
</script>

<style scoped>
.playground-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  gap: 12px;
}

.pg-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}
.pg-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pg-header-left h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.pg-body {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.pg-history {
  width: 300px;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.pg-history .pg-panel-title {
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
}
.pg-history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.pg-history-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.pg-history-item:hover { background: #f5f7fa; }
.pg-history-item.active { background: #ecf5ff; border-color: #b3d8ff; }
.history-item-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.history-status { padding: 0 4px; min-width: 24px; text-align: center; }
.history-scene { font-size: 13px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-time { font-size: 11px; color: #909399; white-space: nowrap; }
.history-item-bottom { display: flex; align-items: center; justify-content: space-between; padding-left: 30px; }
.history-chain { font-size: 12px; color: #606266; font-family: monospace; }
.history-cost { font-size: 12px; color: #909399; font-family: monospace; }
.pg-history-pager { padding: 8px 16px; border-top: 1px solid #e4e7ed; display: flex; justify-content: center; }

.pg-workspace {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.pg-section {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}
.pg-section .pg-panel-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pg-response-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 200px;
}

.pg-url-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.pg-scene-select { width: 220px; }
.pg-app-select { width: 160px; }
.scene-option { display: flex; flex-direction: column; }
.scene-option-desc { font-size: 11px; color: #909399; }
.pg-method-tag { font-weight: 700; font-family: monospace; }
.pg-url-display {
  font-size: 13px; color: #606266; font-family: monospace;
  flex: 1; padding: 6px 10px; background: #f5f7fa; border-radius: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

.pg-scene-desc {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; background: #fdf6ec; border-radius: 4px;
  margin-bottom: 12px; font-size: 13px; color: #606266;
}
.pg-desc-text { flex: 1; }
.pg-chain-tag { flex-shrink: 0; }

.pg-section-block {
  margin-bottom: 12px; border: 1px solid #e4e7ed; border-radius: 6px; overflow: hidden;
}
.pg-block-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 12px; background: #fafafa; cursor: pointer;
  font-size: 13px; font-weight: 500; user-select: none;
}
.pg-block-header .el-icon { transition: transform 0.2s; }
.pg-block-header .el-icon.rotated { transform: rotate(90deg); }
.pg-block-body { padding: 8px 12px; }
.pg-body-type .el-radio-button__inner { padding: 4px 12px; font-size: 12px; }

.pg-header-row, .pg-form-row {
  display: flex; align-items: center; gap: 8px; margin-bottom: 6px;
}
.pg-header-key, .pg-form-key { width: 200px; }
.pg-header-value, .pg-form-value { flex: 1; }
.pg-add-header-btn { margin-top: 4px; }

.pg-code-editor {
  width: 100%; min-height: 140px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px; line-height: 1.6; padding: 10px 12px;
  border: 1px solid #e4e7ed; border-radius: 4px; resize: vertical;
  background: #fafafa; color: #303133; outline: none; transition: border-color 0.2s;
}
.pg-code-editor:focus { border-color: #409eff; }
.pg-editor-toolbar { display: flex; gap: 4px; margin-bottom: 6px; }

.pg-actions { display: flex; gap: 12px; align-items: center; }

.pg-response-summary {
  display: flex; align-items: center; gap: 16px;
  padding: 10px 12px; background: #f5f7fa; border-radius: 6px; margin-bottom: 12px;
}
.pg-response-status { font-weight: 600; }
.pg-response-stat { display: flex; align-items: center; gap: 4px; font-size: 13px; color: #606266; }
.pg-response-empty { margin: 20px 0; }

.pg-response-body {
  background: #1e1e1e; color: #d4d4d4; padding: 14px 16px; border-radius: 6px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px; line-height: 1.5; overflow-x: auto; max-height: 260px; overflow-y: auto;
}
.pg-response-actions { display: flex; gap: 8px; margin-top: 10px; }

.pg-history-detail { margin-top: 8px; }
.pg-detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 16px; padding: 8px 0; }
.pg-detail-item label { font-size: 12px; color: #909399; margin-right: 6px; }
.pg-detail-item span { font-size: 13px; }
.pg-detail-json { margin: 4px 0 0; font-family: monospace; font-size: 12px; background: #f5f7fa; padding: 6px 8px; border-radius: 4px; white-space: pre-wrap; }

.pg-mobile-tabs {
  display: none;
}

@media (min-width: 768px) and (max-width: 1023px) {
  .playground-container--tablet {
    height: calc(100vh - 108px);
    height: calc(100dvh - 108px);
  }

  .playground-container--tablet .pg-history {
    width: 220px;
    min-width: 200px;
  }

  .playground-container--tablet .pg-section {
    padding: 12px;
  }

  .playground-container--tablet .pg-url-bar {
    flex-wrap: wrap;
  }

  .playground-container--tablet .pg-app-select {
    width: 140px;
  }

  .playground-container--tablet .pg-scene-select {
    width: 180px;
  }

  .playground-container--tablet .pg-url-display {
    flex: 1 1 100%;
    white-space: normal;
    word-break: break-all;
  }
}

@media (max-width: 767px) {
  .playground-container--mobile {
    height: calc(100vh - 52px);
    height: calc(100dvh - 52px);
    margin: 0 -12px;
    width: calc(100% + 24px);
    gap: 8px;
  }

  .pg-mobile-tabs {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }

  .pg-mobile-tabs .el-button {
    flex: 1;
    margin: 0;
  }

  .playground-container--mobile .pg-header {
    padding: 0 12px;
  }

  .playground-container--mobile .pg-header-left h2 {
    font-size: 16px;
  }

  .playground-container--mobile .pg-body {
    flex-direction: column;
    min-height: 0;
    padding: 0 12px 12px;
  }

  .playground-container--mobile .pg-history,
  .playground-container--mobile .pg-workspace {
    flex: 1;
    min-height: 0;
    width: 100%;
  }

  .playground-container--mobile .pg-history {
    min-width: 0;
  }

  .playground-container--mobile .pg-section {
    padding: 12px;
  }

  .playground-container--mobile .pg-url-bar {
    flex-wrap: wrap;
    align-items: stretch;
  }

  .playground-container--mobile .pg-app-select,
  .playground-container--mobile .pg-scene-select {
    width: 100%;
    flex: 1 1 100%;
  }

  .playground-container--mobile .pg-method-tag {
    flex-shrink: 0;
  }

  .playground-container--mobile .pg-url-display {
    flex: 1 1 100%;
    white-space: normal;
    word-break: break-all;
  }

  .playground-container--mobile .pg-scene-desc {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .playground-container--mobile .pg-block-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .playground-container--mobile .pg-header-row,
  .playground-container--mobile .pg-form-row {
    flex-wrap: wrap;
  }

  .playground-container--mobile .pg-header-key,
  .playground-container--mobile .pg-form-key,
  .playground-container--mobile .pg-header-value,
  .playground-container--mobile .pg-form-value {
    width: 100%;
    flex: 1 1 100%;
  }

  .playground-container--mobile .pg-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .playground-container--mobile .pg-action-btn {
    width: 100%;
    margin: 0;
  }

  .playground-container--mobile .pg-response-summary {
    flex-wrap: wrap;
    gap: 8px;
  }

  .playground-container--mobile .pg-response-actions {
    flex-wrap: wrap;
  }

  .playground-container--mobile .pg-response-actions .el-button {
    flex: 1 1 calc(50% - 4px);
    min-width: 0;
    margin: 0;
  }

  .playground-container--mobile .pg-response-body {
    max-height: 40vh;
  }

  .pg-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
