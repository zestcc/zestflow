<template>
  <div class="settings-alert-page">
    <div class="page-header">
      <h3 class="page-title">{{ $t('settings.alert.title') }}</h3>
      <p class="page-desc">{{ $t('settings.alert.description') }}</p>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="hint-alert"
      :title="$t('settings.alert.recipientHint')"
    />

    <el-tabs v-model="activeTab" class="alert-tabs">
      <el-tab-pane :label="$t('settings.alert.tabConfig')" name="config">
        <el-card v-loading="configLoading" shadow="never">
          <el-form ref="formRef" :model="form" label-width="160px" class="alert-form">
            <el-form-item :label="$t('settings.alert.enabled')">
              <el-switch v-model="form.enabled" />
            </el-form-item>

            <el-divider content-position="left">{{ $t('settings.alert.sectionThreshold') }}</el-divider>

            <el-form-item :label="$t('settings.alert.windowMinutes')">
              <el-input-number v-model="form.windowMinutes" :min="1" :max="10080" />
              <span class="field-unit">{{ $t('settings.alert.unitMinute') }}</span>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.minExecutions')">
              <el-input-number v-model="form.minExecutions" :min="0" :max="100000" />
            </el-form-item>
            <el-form-item :label="$t('settings.alert.successRateThreshold')">
              <el-input-number v-model="form.successRateThreshold" :min="0" :max="100" :precision="1" />
              <span class="field-unit">%</span>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.failCountThreshold')">
              <el-input-number v-model="form.failCountThreshold" :min="1" :max="100000" />
            </el-form-item>
            <el-form-item :label="$t('settings.alert.p95CostMsThreshold')">
              <el-input-number v-model="form.p95CostMsThreshold" :min="1" :max="3600000" :step="100" />
              <span class="field-unit">ms</span>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.scheduleFailThreshold')">
              <el-input-number v-model="form.scheduleFailThreshold" :min="1" :max="10000" />
            </el-form-item>
            <el-form-item :label="$t('settings.alert.alertNoOnlineExecutor')">
              <el-switch v-model="form.alertNoOnlineExecutor" />
            </el-form-item>

            <el-divider content-position="left">{{ $t('settings.alert.sectionNotify') }}</el-divider>

            <el-form-item :label="$t('settings.alert.cooldownMinutes')">
              <el-input-number v-model="form.cooldownMinutes" :min="1" :max="1440" />
              <span class="field-unit">{{ $t('settings.alert.unitMinute') }}</span>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.subjectPrefix')">
              <el-input v-model="form.subjectPrefix" maxlength="64" show-word-limit class="alert-form-control" />
            </el-form-item>
            <el-form-item :label="$t('settings.alert.scanIntervalMs')">
              <span class="readonly-value">{{ formatScanInterval(config?.scanIntervalMs) }}</span>
              <div class="field-hint">{{ $t('settings.alert.scanIntervalHint') }}</div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
              <el-button :loading="resetting" @click="handleReset">{{ $t('settings.alert.resetDefaults') }}</el-button>
              <el-button :loading="scanning" @click="handleScanNow">{{ $t('settings.alert.scanNow') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.alert.tabHistory')" name="history">
        <el-card shadow="never">
          <el-form :inline="true" :model="historyQuery" size="default" class="responsive-filter-form history-filter">
            <el-form-item :label="$t('settings.appCode')">
              <el-select v-model="historyQuery.appCode" clearable filterable class="page-filter-control">
                <el-option
                  v-for="m in modules"
                  :key="m.appCode"
                  :label="m.appName || m.appCode"
                  :value="m.appCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.rule')">
              <el-select v-model="historyQuery.ruleCode" clearable class="page-filter-control--sm">
                <el-option
                  v-for="r in ruleOptions"
                  :key="r.value"
                  :label="r.label"
                  :value="r.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('settings.alert.timeRange')">
              <el-date-picker
                v-model="historyQuery.timeRange"
                type="datetimerange"
                :start-placeholder="$t('settings.alert.timeStart')"
                :end-placeholder="$t('settings.alert.timeEnd')"
                :shortcuts="timeShortcuts"
                value-format="YYYY-MM-DDTHH:mm:ss"
                class="history-date-range"
                clearable
              />
            </el-form-item>
            <el-form-item class="filter-actions-item">
              <el-button type="primary" @click="loadHistory">{{ $t('common.search') }}</el-button>
              <el-button @click="resetHistoryQuery">{{ $t('common.reset') }}</el-button>
              <el-button :loading="scanning" @click="handleScanNow">{{ $t('settings.alert.scanNow') }}</el-button>
            </el-form-item>
          </el-form>

          <ResponsiveTable
            :data="historyList"
            :columns="historyColumns"
            :loading="historyLoading"
            row-key="id"
            :show-actions="true"
            :actions-label="$t('common.actions')"
            :actions-width="180"
          >
            <template #mailSent="{ row }">
              <el-tag size="small" :type="row.mailSent ? 'success' : 'info'">
                {{ row.mailSent ? $t('settings.alert.mailSent') : $t('settings.alert.mailLogOnly') }}
              </el-tag>
            </template>
            <template #sentAt="{ row }">
              {{ formatTime(row.sentAt) }}
            </template>
            <template #actions="{ row }">
              <el-button text type="primary" size="small" @click="openDetail(row)">
                {{ $t('common.detail') }}
              </el-button>
              <el-button text type="primary" size="small" @click="jumpToRelated(row)">
                {{ jumpLabel(row) }}
              </el-button>
            </template>
          </ResponsiveTable>

          <div class="history-pagination">
            <el-pagination
              v-model:current-page="historyQuery.page"
              v-model:page-size="historyQuery.size"
              :total="historyTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @change="loadHistory"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" :title="$t('settings.alert.detailTitle')" size="420px">
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('settings.appCode')">{{ detailRow.appCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('settings.alert.rule')">{{ detailRow.ruleLabel }}</el-descriptions-item>
          <el-descriptions-item :label="$t('settings.alert.summary')">{{ detailRow.summary }}</el-descriptions-item>
          <el-descriptions-item :label="$t('settings.alert.recipients')">
            {{ detailRow.recipients || '-' }} ({{ detailRow.recipientCount || 0 }})
          </el-descriptions-item>
          <el-descriptions-item :label="$t('settings.alert.sentAt')">{{ formatTime(detailRow.sentAt) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailMetrics.length" class="metrics-block">
          <h4>{{ $t('settings.alert.metrics') }}</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="item in detailMetrics" :key="item.key" :label="item.key">
              {{ item.value }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <div class="detail-actions">
          <el-button type="primary" @click="jumpToRelated(detailRow)">
            {{ jumpLabel(detailRow) }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { alertApi, type AlertConfigVO, type AlertHistoryVO } from '@/api/alert'
import { executorApi, type AppOption } from '@/api/executor'

const { t } = useI18n()
const router = useRouter()

const activeTab = ref('config')
const configLoading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const scanning = ref(false)
const config = ref<AlertConfigVO | null>(null)

const form = reactive({
  enabled: true,
  cooldownMinutes: 30,
  windowMinutes: 60,
  minExecutions: 5,
  successRateThreshold: 95,
  failCountThreshold: 10,
  p95CostMsThreshold: 5000,
  scheduleFailThreshold: 3,
  alertNoOnlineExecutor: true,
  subjectPrefix: '[ZestFlow 告警]',
})

const modules = ref<AppOption[]>([])
const historyLoading = ref(false)
const historyList = ref<AlertHistoryVO[]>([])
const historyTotal = ref(0)
const historyQuery = reactive({
  appCode: '',
  ruleCode: '',
  timeRange: null as [string, string] | null,
  page: 1,
  size: 20,
})

const timeShortcuts = computed(() => [
  {
    text: t('settings.alert.range24h'),
    value: () => {
      const end = new Date()
      const start = new Date(end.getTime() - 24 * 3600 * 1000)
      return [start, end]
    },
  },
  {
    text: t('settings.alert.range7d'),
    value: () => {
      const end = new Date()
      const start = new Date(end.getTime() - 7 * 24 * 3600 * 1000)
      return [start, end]
    },
  },
  {
    text: t('settings.alert.range30d'),
    value: () => {
      const end = new Date()
      const start = new Date(end.getTime() - 30 * 24 * 3600 * 1000)
      return [start, end]
    },
  },
])

const detailVisible = ref(false)
const detailRow = ref<AlertHistoryVO | null>(null)

const ruleOptions = computed(() => [
  { value: 'LOW_SUCCESS_RATE', label: t('settings.alert.rules.LOW_SUCCESS_RATE') },
  { value: 'HIGH_FAIL_COUNT', label: t('settings.alert.rules.HIGH_FAIL_COUNT') },
  { value: 'SLOW_P95', label: t('settings.alert.rules.SLOW_P95') },
  { value: 'NO_ONLINE_EXECUTOR', label: t('settings.alert.rules.NO_ONLINE_EXECUTOR') },
  { value: 'SCHEDULE_FAILURES', label: t('settings.alert.rules.SCHEDULE_FAILURES') },
])

const historyColumns = computed(() => [
  { prop: 'sentAt', label: t('settings.alert.sentAt'), minWidth: 160 },
  { prop: 'appCode', label: t('settings.appCode'), minWidth: 100 },
  { prop: 'ruleLabel', label: t('settings.alert.rule'), minWidth: 120 },
  { prop: 'summary', label: t('settings.alert.summary'), minWidth: 180, showOverflowTooltip: true },
  { prop: 'recipientCount', label: t('settings.alert.recipientCount'), width: 90 },
  { prop: 'mailSent', label: t('settings.alert.mailStatus'), width: 110 },
])

const detailMetrics = computed(() => {
  if (!detailRow.value?.metrics) return []
  return Object.entries(detailRow.value.metrics).map(([key, value]) => ({ key, value }))
})

function applyConfig(data: AlertConfigVO) {
  config.value = data
  form.enabled = data.enabled
  form.cooldownMinutes = data.cooldownMinutes
  form.windowMinutes = data.windowMinutes
  form.minExecutions = data.minExecutions
  form.successRateThreshold = data.successRateThreshold
  form.failCountThreshold = data.failCountThreshold
  form.p95CostMsThreshold = data.p95CostMsThreshold
  form.scheduleFailThreshold = data.scheduleFailThreshold
  form.alertNoOnlineExecutor = data.alertNoOnlineExecutor
  form.subjectPrefix = data.subjectPrefix
}

async function loadConfig() {
  configLoading.value = true
  try {
    const data = await alertApi.getConfig()
    applyConfig(data)
  } catch {
    ElMessage.error(t('settings.alert.loadFailed'))
  } finally {
    configLoading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const data = await alertApi.saveConfig({ ...form })
    applyConfig(data)
    ElMessage.success(t('settings.alert.saveSuccess'))
  } catch {
    ElMessage.error(t('settings.alert.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleReset() {
  try {
    await ElMessageBox.confirm(t('settings.alert.resetConfirm'), t('common.confirm'), { type: 'warning' })
  } catch {
    return
  }
  resetting.value = true
  try {
    const data = await alertApi.resetConfig()
    applyConfig(data)
    ElMessage.success(t('settings.alert.resetSuccess'))
  } catch {
    ElMessage.error(t('settings.alert.saveFailed'))
  } finally {
    resetting.value = false
  }
}

async function handleScanNow() {
  scanning.value = true
  try {
    const res = await alertApi.scanNow()
    if (res.success) {
      ElMessage.success(res.summary ? t('settings.alert.scanSuccessWithSummary', { summary: res.summary }) : t('settings.alert.scanSuccess'))
    } else {
      ElMessage.warning(res.errorMessage || t('settings.alert.scanFailed'))
    }
    if (activeTab.value === 'history') {
      await loadHistory()
    }
  } catch {
    ElMessage.error(t('settings.alert.scanFailed'))
  } finally {
    scanning.value = false
  }
}

async function loadModules() {
  try {
    modules.value = await executorApi.listApps(false)
  } catch {
    modules.value = []
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await alertApi.listHistory({
      appCode: historyQuery.appCode || undefined,
      ruleCode: historyQuery.ruleCode || undefined,
      startTime: historyQuery.timeRange?.[0],
      endTime: historyQuery.timeRange?.[1],
      page: historyQuery.page,
      size: historyQuery.size,
    })
    historyList.value = res.records || []
    historyTotal.value = res.total || 0
  } catch {
    historyList.value = []
    historyTotal.value = 0
  } finally {
    historyLoading.value = false
  }
}

function resetHistoryQuery() {
  historyQuery.appCode = ''
  historyQuery.ruleCode = ''
  historyQuery.timeRange = null
  historyQuery.page = 1
  loadHistory()
}

function jumpLabel(row: AlertHistoryVO) {
  if (row.ruleCode === 'SCHEDULE_FAILURES') {
    return t('settings.alert.viewSchedules')
  }
  if (row.ruleCode === 'NO_ONLINE_EXECUTOR') {
    return t('settings.alert.viewExecutors')
  }
  return t('settings.alert.viewLogs')
}

function jumpToRelated(row: AlertHistoryVO | null) {
  if (!row?.appCode) return
  detailVisible.value = false
  if (row.ruleCode === 'SCHEDULE_FAILURES') {
    router.push({ path: '/schedules', query: { tab: 'logs' } })
    return
  }
  if (row.ruleCode === 'NO_ONLINE_EXECUTOR') {
    router.push({ path: '/executors', query: { appCode: row.appCode } })
    return
  }
  router.push({ path: '/logs', query: { appCode: row.appCode, tab: 'analytics' } })
}

function openDetail(row: AlertHistoryVO) {
  detailRow.value = row
  detailVisible.value = true
}

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatScanInterval(ms?: number) {
  if (!ms) return '-'
  const minutes = Math.round(ms / 60000)
  return `${minutes} ${t('settings.alert.unitMinute')} (${ms} ms)`
}

onMounted(async () => {
  await Promise.all([loadConfig(), loadModules(), loadHistory()])
})
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.hint-alert {
  margin-bottom: 16px;
}

.alert-tabs {
  margin-top: 4px;
}

.alert-form {
  max-width: 680px;
}

.alert-form-control {
  width: 100%;
}

.field-unit {
  margin-left: 8px;
  font-size: 13px;
  color: #909399;
}

.field-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}

.readonly-value {
  font-size: 14px;
  color: #606266;
}

.history-filter {
  margin-bottom: 12px;
}

.history-date-range {
  width: 360px;
  max-width: 100%;
}

.detail-actions {
  margin-top: 16px;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.metrics-block {
  margin-top: 16px;
}

.metrics-block h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
