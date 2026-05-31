<template>
  <div class="playground-container">
    <!-- 顶栏 -->
    <div class="pg-header">
      <div class="pg-header-left">
        <el-icon :size="22" color="#409eff"><Connection /></el-icon>
        <h2>{{ $t('playground.title') }}</h2>
      </div>
      <el-tag type="warning" size="small" effect="plain">{{ $t('playground.modeTag') }}</el-tag>
    </div>

    <!-- 主内容区 -->
    <div class="pg-body">
      <!-- 左栏：历史记录 -->
      <div class="pg-history">
        <div class="pg-panel-title">
          <span>{{ $t('playground.history') }}</span>
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
              <el-tag
                :type="item.status === 1 ? 'success' : 'danger'"
                size="small"
                class="history-status"
              >
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
          <el-empty v-if="!historyLoading && historyList.length === 0" :description="$t('playground.noHistory')" />
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
      <div class="pg-workspace">
        <!-- 请求区 -->
        <div class="pg-section">
          <div class="pg-panel-title">{{ $t('playground.request') }}</div>

          <!-- 场景选择 + URL -->
          <div class="pg-url-bar">
            <el-select
              v-model="selectedScene"
              :placeholder="$t('playground.selectScene')"
              class="pg-scene-select"
              @change="onSceneChange"
            >
              <el-option
                v-for="s in scenes"
                :key="s.id"
                :label="s.name"
                :value="s.id"
              >
                <div class="scene-option">
                  <span>{{ s.name }}</span>
                  <span class="scene-option-desc">{{ s.description }}</span>
                </div>
              </el-option>
            </el-select>
            <el-tag type="primary" size="small" class="pg-method-tag">POST</el-tag>
            <div class="pg-url-display">{{ baseUrl }}/api/playground/execute/{{ selectedScene || '{scene}' }}</div>
          </div>

          <!-- 场景描述 -->
          <div class="pg-scene-desc" v-if="currentScene">
            <el-icon><InfoFilled /></el-icon>
            <span class="pg-desc-text">{{ currentScene.description }}</span>
            <el-tag size="small" type="info" class="pg-chain-tag">
              {{ $t('playground.chainCode') }}: {{ chainCode }}
            </el-tag>
          </div>

          <!-- 自定义请求头 -->
          <div class="pg-section-block">
            <div class="pg-block-header" @click="headersExpanded = !headersExpanded">
              <span>{{ $t('playground.headers') }}</span>
              <el-icon :class="{ rotated: headersExpanded }"><ArrowRight /></el-icon>
            </div>
            <div v-show="headersExpanded" class="pg-block-body">
              <div v-for="(h, i) in customHeaders" :key="i" class="pg-header-row">
                <el-input
                  v-model="h.key"
                  :placeholder="$t('playground.headerKey')"
                  size="small"
                  class="pg-header-key"
                />
                <el-input
                  v-model="h.value"
                  :placeholder="$t('playground.headerValue')"
                  size="small"
                  class="pg-header-value"
                />
                <el-button
                  type="danger"
                  :icon="Delete"
                  size="small"
                  circle
                  @click="customHeaders.splice(i, 1)"
                />
              </div>
              <el-button size="small" :icon="Plus" @click="addHeader" class="pg-add-header-btn">
                {{ $t('playground.addHeader') }}
              </el-button>
            </div>
          </div>

          <!-- 请求体 -->
          <div class="pg-section-block">
            <div class="pg-block-header">
              <span>{{ $t('playground.body') }}</span>
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
                  <el-button size="small" text @click="formatJson">{{ $t('playground.format') }}</el-button>
                  <el-button size="small" text @click="resetJson">{{ $t('playground.reset') }}</el-button>
                </div>
                <textarea
                  v-model="requestBody"
                  class="pg-code-editor"
                  :placeholder="$t('playground.bodyPlaceholder')"
                  spellcheck="false"
                  rows="8"
                />
              </div>
              <!-- Form 编辑器 -->
              <div v-show="bodyType === 'form'">
                <div v-for="(f, i) in formFields" :key="i" class="pg-form-row">
                  <el-input v-model="f.key" :placeholder="$t('playground.formKey')" size="small" class="pg-form-key" />
                  <el-input v-model="f.value" :placeholder="$t('playground.formValue')" size="small" class="pg-form-value" />
                  <el-button type="danger" :icon="Delete" size="small" circle @click="formFields.splice(i, 1)" />
                </div>
                <el-button size="small" :icon="Plus" @click="formFields.push({ key: '', value: '' })">
                  {{ $t('playground.addField') }}
                </el-button>
              </div>
              <!-- Raw 编辑器 -->
              <div v-show="bodyType === 'raw'">
                <textarea
                  v-model="requestRawBody"
                  class="pg-code-editor"
                  :placeholder="$t('playground.rawPlaceholder')"
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
              size="large"
              :loading="executing"
              :disabled="!selectedScene"
              @click="handleExecute"
            >
              {{ $t('playground.execute') }}
            </el-button>
            <el-button
              :icon="Delete"
              size="large"
              :disabled="historyList.length === 0"
              @click="handleClearHistory"
            >
              {{ $t('playground.clearHistory') }}
            </el-button>
          </div>
        </div>

        <!-- 响应区 -->
        <div class="pg-section pg-response-section">
          <div class="pg-panel-title">{{ $t('playground.response') }}</div>

          <!-- 响应摘要 -->
          <div class="pg-response-summary" v-if="lastResult">
            <el-tag
              :type="lastResult.status === 1 ? 'success' : 'danger'"
              size="default"
              class="pg-response-status"
            >
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
            <el-empty :description="$t('playground.noResponse')" />
          </div>

          <!-- 响应体 -->
          <div class="pg-block-body" v-if="lastResult">
            <pre class="pg-response-body"><code>{{ formatResponseBody }}</code></pre>
            <div class="pg-response-actions">
              <el-button size="small" :icon="Link" @click="goToLogs(lastResult.instanceId)" :disabled="!lastResult.instanceId">
                {{ $t('playground.viewLogs') }}
              </el-button>
              <el-button size="small" :icon="CopyDocument" @click="copyResponse">
                {{ $t('playground.copy') }}
              </el-button>
            </div>
          </div>

          <!-- 历史明细详情 -->
          <div v-if="historyDetail" class="pg-history-detail">
            <el-divider />
            <div class="pg-panel-title">{{ $t('playground.historyDetail') }}</div>
            <div class="pg-detail-grid">
              <div class="pg-detail-item">
                <label>{{ $t('playground.scene') }}:</label>
                <span>{{ historyDetail.sceneName }}</span>
              </div>
              <div class="pg-detail-item">
                <label>{{ $t('playground.chainCode') }}:</label>
                <span>{{ historyDetail.chainCode }}</span>
              </div>
              <div class="pg-detail-item">
                <label>{{ $t('playground.instanceId') }}:</label>
                <el-button size="small" link @click="goToLogs(historyDetail.instanceId)">
                  {{ historyDetail.instanceId || '-' }}
                </el-button>
              </div>
              <div class="pg-detail-item" v-if="historyDetail.requestHeaders && Object.keys(historyDetail.requestHeaders).length">
                <label>{{ $t('playground.headers') }}:</label>
                <pre class="pg-detail-json">{{ JSON.stringify(historyDetail.requestHeaders, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Connection, VideoPlay, Plus, Delete, ArrowRight, InfoFilled,
  Timer, WarningFilled, Link, CopyDocument,
} from '@element-plus/icons-vue'
import { listScenes, executeScene, queryHistory, getHistoryDetail, type SceneInfo, type ExecuteResult, type HistoryItem } from '@/api/playground'

const { t } = useI18n()
const router = useRouter()

// === 场景 ===
const scenes = ref<SceneInfo[]>([])
const selectedScene = ref('')
const currentScene = computed(() => scenes.value.find(s => s.id === selectedScene.value))
const chainCode = computed(() => currentScene.value?.id ? `CHN_DEMO_${currentScene.value.id.toUpperCase()}` : '')
const baseUrl = ref(window.location.origin)

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
const lastResult = ref<ExecuteResult | null>(null)
const executeError = ref('')

async function handleExecute() {
  if (!selectedScene.value) return
  executing.value = true
  executeError.value = ''
  lastResult.value = null

  try {
    let params: Record<string, any>
    if (bodyType.value === 'json') {
      try {
        params = JSON.parse(requestBody.value)
      } catch {
        ElMessage.warning(t('playground.invalidJson'))
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

    const headers: Record<string, string> = {}
    for (const h of customHeaders.value) {
      if (h.key) headers[h.key] = h.value
    }

    const res = await executeScene(selectedScene.value, params, headers)
    lastResult.value = res.data || res
    await loadHistory()
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
    ElMessage.warning(t('playground.invalidJson'))
  }
}

function resetJson() {
  const dp = currentScene.value?.defaultParams
  requestBody.value = dp ? JSON.stringify(dp, null, 2) : '{\n  \n}'
}

const formatResponseBody = computed(() => {
  if (!lastResult.value) return ''
  try {
    return JSON.stringify(lastResult.value, null, 2)
  } catch {
    return String(lastResult.value)
  }
})

function copyResponse() {
  navigator.clipboard.writeText(formatResponseBody.value)
  ElMessage.success(t('playground.copied'))
}

function goToLogs(instanceId: string) {
  if (instanceId) {
    router.push({ name: 'Logs', query: { executionId: instanceId } })
  }
}

// === 场景切换 ===
function onSceneChange(sceneId: string) {
  const scene = scenes.value.find(s => s.id === sceneId)
  if (scene?.defaultParams) {
    requestBody.value = JSON.stringify(scene.defaultParams, null, 2)
  }
  customHeaders.value = []
  formFields.value = []
  bodyType.value = 'json'
  lastResult.value = null
}

// === 历史 ===
const historyList = ref<HistoryItem[]>([])
const totalHistory = ref(0)
const currentPage = ref(1)
const size = ref(20)
const historyLoading = ref(false)
const selectedHistoryId = ref<number | null>(null)
const historyDetail = ref<HistoryItem | null>(null)

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await queryHistory(currentPage.value, size.value)
    const data = res.data || res
    historyList.value = data.list || []
    totalHistory.value = data.total || 0
  } catch {
    // silent
  } finally {
    historyLoading.value = false
  }
}

async function loadHistoryDetail(item: HistoryItem) {
  selectedHistoryId.value = item.id
  try {
    const res = await getHistoryDetail(item.id)
    historyDetail.value = res.data || res
  } catch {
    historyDetail.value = item
  }
}

function handleClearHistory() {
  // 仅清空选中状态
  selectedHistoryId.value = null
  historyDetail.value = null
  lastResult.value = null
}

// === 工具 ===
function formatTime(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// === 初始化 ===
onMounted(async () => {
  try {
    const res = await listScenes()
    scenes.value = res.data || []
    if (scenes.value.length > 0) {
      selectedScene.value = scenes.value[0].id
      onSceneChange(selectedScene.value)
    }
  } catch {
    ElMessage.error(t('playground.loadFailed'))
  }
  await loadHistory()
})
</script>

<style scoped>
.playground-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  gap: 12px;
}

/* 顶栏 */
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

/* 主体 */
.pg-body {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

/* 左栏：历史 */
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
.pg-history-item:hover {
  background: #f5f7fa;
}
.pg-history-item.active {
  background: #ecf5ff;
  border-color: #b3d8ff;
}
.history-item-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.history-status {
  padding: 0 4px;
  min-width: 24px;
  text-align: center;
}
.history-scene {
  font-size: 13px;
  font-weight: 500;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
.history-item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 30px;
}
.history-chain {
  font-size: 12px;
  color: #606266;
  font-family: monospace;
}
.history-cost {
  font-size: 12px;
  color: #909399;
  font-family: monospace;
}
.pg-history-pager {
  padding: 8px 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  justify-content: center;
}

/* 右栏工作区 */
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

/* URL 栏 */
.pg-url-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.pg-scene-select {
  width: 220px;
}
.scene-option {
  display: flex;
  flex-direction: column;
}
.scene-option-desc {
  font-size: 11px;
  color: #909399;
}
.pg-method-tag {
  font-weight: 700;
  font-family: monospace;
}
.pg-url-display {
  font-size: 13px;
  color: #606266;
  font-family: monospace;
  flex: 1;
  padding: 6px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 场景描述 */
.pg-scene-desc {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.pg-desc-text {
  flex: 1;
}
.pg-chain-tag {
  flex-shrink: 0;
}

/* 区块折叠 */
.pg-section-block {
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.pg-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #fafafa;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  user-select: none;
}
.pg-block-header .el-icon {
  transition: transform 0.2s;
}
.pg-block-header .el-icon.rotated {
  transform: rotate(90deg);
}
.pg-block-body {
  padding: 8px 12px;
}
.pg-body-type .el-radio-button__inner {
  padding: 4px 12px;
  font-size: 12px;
}

/* 请求头行 */
.pg-header-row,
.pg-form-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.pg-header-key,
.pg-form-key {
  width: 200px;
}
.pg-header-value,
.pg-form-value {
  flex: 1;
}
.pg-add-header-btn {
  margin-top: 4px;
}

/* 代码编辑器 */
.pg-code-editor {
  width: 100%;
  min-height: 140px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  resize: vertical;
  background: #fafafa;
  color: #303133;
  outline: none;
  transition: border-color 0.2s;
}
.pg-code-editor:focus {
  border-color: #409eff;
}
.pg-editor-toolbar {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}

/* 动作按钮 */
.pg-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 响应区 */
.pg-response-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
}
.pg-response-status {
  font-weight: 600;
}
.pg-response-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}
.pg-response-empty {
  margin: 20px 0;
}

/* 响应体 */
.pg-response-body {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 14px 16px;
  border-radius: 6px;
  font-family: 'SF Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow-x: auto;
  max-height: 260px;
  overflow-y: auto;
}
.pg-response-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

/* 历史详情 */
.pg-history-detail {
  margin-top: 8px;
}
.pg-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 8px 0;
}
.pg-detail-item label {
  font-size: 12px;
  color: #909399;
  margin-right: 6px;
}
.pg-detail-item span {
  font-size: 13px;
}
.pg-detail-json {
  margin: 4px 0 0;
  font-family: monospace;
  font-size: 12px;
  background: #f5f7fa;
  padding: 6px 8px;
  border-radius: 4px;
  white-space: pre-wrap;
}
</style>
