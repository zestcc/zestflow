<template>
  <div class="schedules-page">
    <div class="page-header">
      <div class="page-header-row page-header-row--actions-end">
        <el-button v-if="activeTab === 'chain'" type="primary" @click="showCreate">{{ $t('schedules.create') }}</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="schedule-tabs" @tab-change="onTabChange">
      <el-tab-pane :label="$t('schedules.tabChain')" name="chain" />
      <el-tab-pane :label="$t('schedules.tabPlatform')" name="platform" />
      <el-tab-pane :label="$t('schedules.tabLogs')" name="logs" />
    </el-tabs>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="default" class="responsive-filter-form">
        <el-form-item v-if="activeTab === 'chain'" :label="$t('schedules.app')">
          <el-select v-model="query.appCode" :placeholder="$t('schedules.app')" clearable class="page-filter-control" @change="onAppFilterChange">
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeTab === 'platform'" :label="$t('schedules.module')">
          <el-select v-model="query.module" :placeholder="$t('schedules.module')" clearable class="page-filter-control--sm">
            <el-option
              v-for="item in platformModuleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('schedules.keyword')">
          <el-input v-model="query.keyword" :placeholder="keywordPlaceholder" clearable class="page-filter-control" />
        </el-form-item>
        <el-form-item v-if="activeTab !== 'logs'" :label="$t('common.status')">
          <el-select v-model="query.status" :placeholder="$t('common.status')" clearable class="page-filter-control--sm">
            <el-option :label="$t('schedules.total')" :value="undefined" />
            <el-option
              v-for="item in enableStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.bindValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeTab === 'logs'" :label="$t('schedules.logStatus')">
          <el-select v-model="logQuery.status" clearable class="page-filter-control--sm">
            <el-option
              v-for="item in scheduleLogStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.bindValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions-item">
          <el-button type="primary" @click="search">{{ $t('schedules.search') }}</el-button>
          <el-button @click="resetSearch">{{ $t('schedules.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-card v-if="activeTab !== 'logs'" shadow="never">
      <ResponsiveTable
        :data="displayList"
        :columns="activeColumns"
        :show-actions="true"
        :actions-label="$t('common.actions')"
        :actions-width="activeTab === 'platform' ? 200 : 240"
      >
        <template #jobType="{ row }">
          <el-tag size="small" :type="row.jobType === 'PLATFORM' ? 'info' : 'primary'">
            {{ row.jobType === 'PLATFORM' ? $t('schedules.platformJob') : $t('schedules.chainJob') }}
          </el-tag>
        </template>
        <template #module="{ row }">
          <el-tag size="small">{{ row.module || '-' }}</el-tag>
        </template>
        <template #remote="{ row }">
          <el-tag size="small" :type="row.remote ? 'warning' : 'success'">
            {{ row.remote ? $t('schedules.remoteNode') : $t('schedules.adminLocal') }}
          </el-tag>
        </template>
        <template #routeStrategy="{ row }">
          <el-tag v-if="row.routeStrategy" size="small">{{ $t('schedules.' + row.routeStrategy) }}</el-tag>
          <span v-else>-</span>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('schedules.enabled') : $t('schedules.disabled') }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <template v-if="activeTab === 'chain'">
            <el-button text size="small" type="primary" class="action-btn" @click="showEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" class="action-btn" @click="toggleStatus(row)">
              {{ row.status === 1 ? $t('schedules.disable') : $t('schedules.enable') }}
            </el-button>
            <el-button text size="small" type="success" class="action-btn" @click="handleTrigger(row)">{{ $t('schedules.manualTrigger') }}</el-button>
            <el-button text size="small" type="primary" class="action-btn" @click="showLogs(row)">{{ $t('schedules.viewLogs') }}</el-button>
            <el-button text size="small" type="danger" class="action-btn" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
          <template v-else>
            <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" class="action-btn" @click="toggleStatus(row)">
              {{ row.status === 1 ? $t('schedules.disable') : $t('schedules.enable') }}
            </el-button>
            <el-button v-if="!row.remote" text size="small" type="success" class="action-btn" @click="handleTrigger(row)">{{ $t('schedules.manualTrigger') }}</el-button>
            <el-button text size="small" type="primary" class="action-btn" @click="showLogs(row)">{{ $t('schedules.viewLogs') }}</el-button>
          </template>
        </template>
      </ResponsiveTable>
      <div class="page-pagination-wrap">
        <el-pagination
          v-if="total > 0"
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          :layout="paginationLayout"
          @change="fetchList"
        />
      </div>
    </el-card>

    <!-- 全局日志 -->
    <el-card v-else shadow="never">
      <el-row v-if="scheduleLogStats" :gutter="12" class="schedule-stats-row">
        <el-col :xs="12" :sm="6">
          <div class="mini-stat"><span class="val">{{ scheduleLogStats.totalCount }}</span><span class="lbl">{{ $t('schedules.statTotal') }}</span></div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="mini-stat ok"><span class="val">{{ scheduleLogStats.successRate }}%</span><span class="lbl">{{ $t('schedules.statSuccessRate') }}</span></div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="mini-stat"><span class="val">{{ scheduleLogStats.avgCostMs }}ms</span><span class="lbl">{{ $t('schedules.statAvgCost') }}</span></div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="mini-stat danger"><span class="val">{{ scheduleLogStats.failedCount }}</span><span class="lbl">{{ $t('schedules.statFailed') }}</span></div>
        </el-col>
      </el-row>
      <ResponsiveTable :data="logList" :columns="logColumns" :show-actions="true" :actions-label="$t('common.actions')" :actions-width="120">
        <template #triggerType="{ row }">
          <el-tag size="small" :type="row.triggerType === 'cron' ? '' : 'warning'">
            {{ row.triggerType === 'cron' ? $t('schedules.cronTrigger') : $t('schedules.manual') }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="row.executionId"
            text
            size="small"
            type="primary"
            class="action-btn"
            @click="goExecutionTrace(row.executionId)"
          >{{ $t('schedules.viewTrace') }}</el-button>
        </template>
      </ResponsiveTable>
      <div class="page-pagination-wrap">
        <el-pagination
          v-if="logTotal > 0"
          v-model:current-page="logQuery.page"
          v-model:page-size="logQuery.size"
          :total="logTotal"
          :page-sizes="[10, 20, 50]"
          :layout="paginationLayout"
          @change="fetchGlobalLogs"
        />
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? $t('schedules.edit') : $t('schedules.create')" :width="640" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item v-if="!isEditing" :label="$t('schedules.chainCode')" prop="chainCode">
          <el-select v-model="form.chainCode" :placeholder="$t('schedules.selectChain')" filterable style="width:100%" @change="onChainSelect">
            <el-option v-for="c in chainOptions" :key="c.code" :label="c.code + ' - ' + c.name" :value="c.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-else :label="$t('schedules.chainCode')">
          <el-input :model-value="form.chainCode + ' - ' + form.chainName" disabled />
        </el-form-item>
        <el-form-item :label="$t('schedules.cron')" prop="cron">
          <el-input v-model="form.cron" placeholder="0 0/5 * * * ?" />
        </el-form-item>
        <el-form-item :label="$t('schedules.routeStrategy')">
          <el-select v-model="form.routeStrategy" style="width:200px">
            <el-option v-for="item in routeStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('schedules.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 单任务日志弹窗 -->
    <el-dialog v-model="logDialogVisible" :title="$t('schedules.executeLog')" :width="900" :close-on-click-modal="false">
      <template v-if="currentSchedule">
        <el-alert :title="logDialogTitle" type="info" :closable="false" show-icon style="margin-bottom:16px" />
      </template>
      <ResponsiveTable :data="logList" :columns="logColumns" :show-actions="true" :actions-label="$t('common.actions')" :actions-width="120">
        <template #triggerType="{ row }">
          <el-tag size="small" :type="row.triggerType === 'cron' ? '' : 'warning'">
            {{ row.triggerType === 'cron' ? $t('schedules.cronTrigger') : $t('schedules.manual') }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="row.executionId"
            text
            size="small"
            type="primary"
            class="action-btn"
            @click="goExecutionTrace(row.executionId)"
          >{{ $t('schedules.viewTrace') }}</el-button>
        </template>
      </ResponsiveTable>
      <div class="page-pagination-wrap">
        <el-pagination
          v-if="logTotal > 0"
          v-model:current-page="logQuery.page"
          v-model:page-size="logQuery.size"
          :total="logTotal"
          :layout="paginationLayout"
          @change="fetchLogs"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scheduleApi, type ScheduleVO, type ScheduleCreateDTO, type ScheduleUpdateDTO, type ScheduleLogVO } from '@/api/schedule'
import { chainApi, type ChainVO } from '@/api/chain'
import { executorApi, type AppOption } from '@/api/executor'
import { useDict } from '@/composables/useDict'
import { useDictLabel } from '@/composables/useDictLabel'
import { useCurrentApp } from '@/composables/useCurrentApp'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { syncFromApps, setCurrentAppCode } = useCurrentApp()
const { paginationLayout } = useResponsivePagination()

const activeTab = ref<'chain' | 'platform' | 'logs'>('chain')

const chainColumns = computed(() => [
  { prop: 'chainCode', label: t('schedules.chainCode'), showOverflowTooltip: true },
  { prop: 'chainName', label: t('schedules.chainName'), showOverflowTooltip: true },
  { prop: 'cron', label: t('schedules.cron'), width: 160 },
  { prop: 'routeStrategy', label: t('schedules.routeStrategy'), width: 120 },
  { prop: 'status', label: t('common.status'), width: 80 },
  { prop: 'updatedAt', label: t('common.updatedAt'), width: 170, showOverflowTooltip: true },
])

const platformColumns = computed(() => [
  { prop: 'chainName', label: t('schedules.jobName'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'jobKey', label: t('schedules.jobKey'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'module', label: t('schedules.module'), width: 100 },
  { prop: 'cron', label: t('schedules.scheduleExpr'), width: 140 },
  { prop: 'remote', label: t('schedules.runLocation'), width: 110 },
  { prop: 'lastTriggerAt', label: t('schedules.lastTriggerAt'), width: 170 },
  { prop: 'status', label: t('common.status'), width: 80 },
])

const activeColumns = computed(() => activeTab.value === 'platform' ? platformColumns.value : chainColumns.value)

const logColumns = computed(() => [
  { prop: 'jobName', label: t('schedules.jobName'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'jobKey', label: t('schedules.jobKey'), minWidth: 180, showOverflowTooltip: true },
  { prop: 'chainCode', label: t('schedules.chainCode'), width: 140, showOverflowTooltip: true },
  { prop: 'triggerType', label: t('schedules.triggerType'), width: 80 },
  { prop: 'executorAddress', label: t('schedules.executorAddress'), width: 160 },
  { prop: 'status', label: t('schedules.logStatus'), width: 80 },
  { prop: 'costMs', label: t('schedules.costMs'), width: 90 },
  { prop: 'errorMessage', label: t('schedules.errorMessage'), minWidth: 150, showOverflowTooltip: true },
  { prop: 'triggeredAt', label: t('schedules.triggerTime'), width: 170 },
])

const keywordPlaceholder = computed(() =>
  activeTab.value === 'platform' ? t('schedules.platformKeyword') : t('schedules.keyword')
)

const list = ref<ScheduleVO[]>([])
const total = ref(0)
const query = reactive({
  appCode: undefined as string | undefined,
  module: undefined as string | undefined,
  keyword: undefined as string | undefined,
  status: undefined as number | undefined,
  page: 1,
  size: 20,
})
const modules = ref<AppOption[]>([])

const displayList = computed(() => {
  if (activeTab.value !== 'platform' || !query.module) return list.value
  return list.value.filter(i => i.module === query.module)
})

const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<any>(null)
const form = reactive({ chainCode: '', chainName: '', cron: '', routeStrategy: 'round_robin', remark: '' })
const chainOptions = ref<ChainVO[]>([])
const { options: routeStrategyOptions } = useDict('route_strategy')
const { dictOptions: enableStatusOptions } = useDictLabel('enable_status')
const { dictOptions: scheduleLogStatusOptions } = useDictLabel('schedule_log_status')
const { options: platformModuleOptions } = useDict('platform_module')

const logDialogVisible = ref(false)
const currentSchedule = ref<ScheduleVO | null>(null)
const logList = ref<ScheduleLogVO[]>([])
const logTotal = ref(0)
const scheduleLogStats = ref<{ totalCount: number; successCount: number; failedCount: number; runningCount: number; successRate: number; avgCostMs: number } | null>(null)
const logQuery = reactive({ scheduleId: undefined as number | undefined, status: undefined as number | undefined, page: 1, size: 10 })

const logDialogTitle = computed(() => {
  if (!currentSchedule.value) return ''
  const s = currentSchedule.value
  return s.jobType === 'PLATFORM' ? `${s.chainName} (${s.jobKey})` : `${s.chainCode} - ${s.cron}`
})

const rules: Record<string, any[]> = {
  chainCode: [{ required: true, message: t('schedules.selectChain'), trigger: 'change' }],
  cron: [{ required: true, message: t('validation.required', { field: t('schedules.cron') }), trigger: 'blur' }],
}

function statusTagType(status: number): string {
  return status === 0 ? 'warning' : status === 1 ? 'success' : 'danger'
}
function statusText(status: number): string {
  return status === 0 ? t('schedules.running') : status === 1 ? t('schedules.success') : t('schedules.failed')
}

function currentJobType() {
  return activeTab.value === 'platform' ? 'PLATFORM' as const : 'CHAIN' as const
}

async function fetchList() {
  const res = await scheduleApi.list({
    keyword: query.keyword,
    jobType: currentJobType(),
    status: query.status,
    page: query.page,
    size: query.size,
  })
  list.value = res.records || []
  total.value = res.total || 0
}

async function fetchGlobalLogs() {
  const res = await scheduleApi.listLogs({
    keyword: query.keyword,
    status: logQuery.status,
    page: logQuery.page,
    size: logQuery.size,
  })
  logList.value = res.records || []
  logTotal.value = res.total || 0
  try {
    scheduleLogStats.value = await scheduleApi.logStats(24)
  } catch {
    scheduleLogStats.value = null
  }
}

function goExecutionTrace(executionId: string) {
  router.push({ path: '/logs', query: { executionId } })
}

async function search() {
  query.page = 1
  logQuery.page = 1
  if (activeTab.value === 'logs') await fetchGlobalLogs()
  else await fetchList()
}

function resetSearch() {
  query.appCode = undefined
  query.module = undefined
  query.keyword = undefined
  query.status = undefined
  logQuery.status = undefined
  query.page = 1
  logQuery.page = 1
  if (activeTab.value === 'logs') fetchGlobalLogs()
  else fetchList()
}

function onTabChange() {
  query.page = 1
  logQuery.page = 1
  if (activeTab.value === 'logs') fetchGlobalLogs()
  else fetchList()
}

async function fetchModules() {
  try {
    modules.value = await executorApi.listApps()
    const code = syncFromApps(modules.value)
    if (code) query.appCode = code
  } catch { modules.value = [] }
}

function onAppFilterChange(code: string | undefined) {
  if (code) setCurrentAppCode(code)
}

async function fetchChains(appCode: string) {
  try {
    const res = await chainApi.list({ appCode, page: 1, size: 999 })
    chainOptions.value = res.records || []
  } catch { chainOptions.value = [] }
}

function resetForm() {
  form.chainCode = ''
  form.chainName = ''
  form.cron = ''
  form.routeStrategy = 'round_robin'
  form.remark = ''
}

function onChainSelect(code: string) {
  form.chainName = chainOptions.value.find(x => x.code === code)?.name || ''
}

async function showCreate() {
  isEditing.value = false
  editingId.value = null
  resetForm()
  fetchChains('')
  dialogVisible.value = true
}

async function showEdit(row: ScheduleVO) {
  isEditing.value = true
  editingId.value = row.id
  form.chainCode = row.chainCode
  form.chainName = row.chainName
  form.cron = row.cron
  form.routeStrategy = row.routeStrategy || 'round_robin'
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEditing.value && editingId.value) {
      await scheduleApi.update(editingId.value, { cron: form.cron, routeStrategy: form.routeStrategy, remark: form.remark })
    } else {
      await scheduleApi.create({ chainCode: form.chainCode, chainName: form.chainName, cron: form.cron, routeStrategy: form.routeStrategy, remark: form.remark })
    }
    dialogVisible.value = false
    ElMessage.success(t('common.save'))
    fetchList()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: ScheduleVO) {
  await scheduleApi.toggleStatus(row.id)
  ElMessage.success(t('common.save'))
  fetchList()
}

async function handleDelete(row: ScheduleVO) {
  await ElMessageBox.confirm(t('schedules.confirmDeleteMsg', { name: row.chainCode }), t('common.confirm'), { type: 'warning' })
  await scheduleApi.delete(row.id)
  ElMessage.success(t('common.save'))
  fetchList()
}

async function handleTrigger(row: ScheduleVO) {
  await ElMessageBox.confirm(t('schedules.triggerConfirm'), t('common.confirm'), { type: 'info' })
  try {
    await scheduleApi.trigger(row.id)
    ElMessage.success(t('schedules.triggerSuccess'))
  } catch (e: any) {
    ElMessage.error(e?.message || t('schedules.triggerFailed'))
  }
  fetchList()
}

async function showLogs(row: ScheduleVO) {
  currentSchedule.value = row
  logQuery.scheduleId = row.id
  logQuery.page = 1
  logDialogVisible.value = true
  fetchLogs()
}

async function fetchLogs() {
  const res = await scheduleApi.listLogs({ scheduleId: logQuery.scheduleId, page: logQuery.page, size: logQuery.size })
  logList.value = res.records || []
  logTotal.value = res.total || 0
}

onMounted(async () => {
  const tab = typeof route.query.tab === 'string' ? route.query.tab.trim() : ''
  if (tab === 'logs') {
    activeTab.value = 'logs'
  }
  await fetchModules()
  if (activeTab.value === 'logs') {
    await fetchGlobalLogs()
  } else {
    await fetchList()
  }
})
</script>

<style scoped>
.schedule-tabs { margin-bottom: 12px; }
.filter-card { margin-bottom: 16px; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
.schedule-stats-row { margin-bottom: 16px; }
.mini-stat {
  text-align: center;
  padding: 12px 8px;
  background: #f5f7fa;
  border-radius: 6px;
}
.mini-stat .val { display: block; font-size: 20px; font-weight: 700; color: #303133; }
.mini-stat .lbl { display: block; font-size: 12px; color: #909399; margin-top: 4px; }
.mini-stat.ok .val { color: #67c23a; }
.mini-stat.danger .val { color: #f56c6c; }
</style>
