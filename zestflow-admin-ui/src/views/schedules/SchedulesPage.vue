<template>
  <div class="schedules-page">
    <div class="page-header">
      <div class="page-header-row">
        <h2>{{ $t('schedules.title') }}</h2>
        <el-button type="primary" @click="showCreate">{{ $t('schedules.create') }}</el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="default" class="responsive-filter-form">
        <el-form-item :label="$t('schedules.app')">
          <el-select v-model="query.appCode" :placeholder="$t('schedules.app')" clearable class="page-filter-control" @change="onAppFilterChange">
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('schedules.keyword')">
          <el-input v-model="query.keyword" :placeholder="$t('schedules.keyword')" clearable class="page-filter-control" />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-select v-model="query.status" :placeholder="$t('common.status')" clearable class="page-filter-control--sm">
            <el-option :label="$t('schedules.total')" :value="undefined" />
            <el-option :label="$t('schedules.enabled')" :value="1" />
            <el-option :label="$t('schedules.disabled')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions-item">
          <el-button type="primary" @click="search">{{ $t('schedules.search') }}</el-button>
          <el-button @click="resetSearch">{{ $t('schedules.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计 -->
    <el-card shadow="never" class="stats-card">
      <div class="stats-row">
        <span class="stat-item total">{{ $t('schedules.total') }}: <b>{{ stats.total }}</b></span>
        <span class="stat-item success"><el-tag type="success" size="small">{{ $t('schedules.enabled') }}: {{ stats.enabled }}</el-tag></span>
        <span class="stat-item danger"><el-tag type="info" size="small">{{ $t('schedules.disabled') }}: {{ stats.disabled }}</el-tag></span>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <ResponsiveTable
        :data="list"
        :columns="scheduleColumns"
        :show-actions="true"
        :actions-label="$t('common.actions')"
        :actions-width="240"
      >
        <template #routeStrategy="{ row }">
          <el-tag size="small">{{ $t('schedules.' + (row.routeStrategy || 'round_robin')) }}</el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('schedules.enabled') : $t('schedules.disabled') }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <el-button text size="small" type="primary" class="action-btn" @click="showEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" class="action-btn" @click="toggleStatus(row)">
            {{ row.status === 1 ? $t('schedules.disable') : $t('schedules.enable') }}
          </el-button>
          <el-button text size="small" type="success" class="action-btn" @click="handleTrigger(row)">{{ $t('schedules.manualTrigger') }}</el-button>
          <el-button text size="small" type="primary" class="action-btn" @click="showLogs(row)">{{ $t('schedules.viewLogs') }}</el-button>
          <el-button text size="small" type="danger" class="action-btn" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
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

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('schedules.edit') : $t('schedules.create')"
      :width="640"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item v-if="!isEditing" :label="$t('schedules.chainCode')" prop="chainCode">
          <el-select v-model="form.chainCode" :placeholder="$t('schedules.selectChain')" filterable style="width:100%" @change="onChainSelect">
            <el-option
              v-for="c in chainOptions"
              :key="c.code"
              :label="c.code + ' - ' + c.name"
              :value="c.code"
            />
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

    <!-- 执行日志弹窗 -->
    <el-dialog
      v-model="logDialogVisible"
      :title="$t('schedules.executeLog')"
      :width="900"
      :close-on-click-modal="false"
    >
      <template v-if="currentSchedule">
        <el-alert :title="currentSchedule.chainCode + ' - ' + currentSchedule.cron" type="info" :closable="false" show-icon style="margin-bottom:16px" />
      </template>
      <ResponsiveTable
        :data="logList"
        :columns="logColumns"
      >
        <template #triggerType="{ row }">
          <el-tag size="small" :type="row.triggerType === 'cron' ? '' : 'warning'">
            {{ row.triggerType === 'cron' ? $t('schedules.cronTrigger') : $t('schedules.manual') }}
          </el-tag>
        </template>
        <template #routeStrategy="{ row }">
          {{ row.routeStrategy ? $t('schedules.' + row.routeStrategy) : '-' }}
        </template>
        <template #status="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusText(row.status) }}
          </el-tag>
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scheduleApi, type ScheduleVO, type ScheduleCreateDTO, type ScheduleUpdateDTO, type ScheduleLogVO } from '@/api/schedule'
import { chainApi, type ChainVO } from '@/api/chain'
import { executorApi, type AppOption } from '@/api/executor'
import { useDict } from '@/composables/useDict'
import { useCurrentApp } from '@/composables/useCurrentApp'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const { syncFromApps, setCurrentAppCode } = useCurrentApp()
const { paginationLayout } = useResponsivePagination()

const scheduleColumns = computed(() => [
  { prop: 'chainCode', label: t('schedules.chainCode'), showOverflowTooltip: true },
  { prop: 'chainName', label: t('schedules.chainName'), showOverflowTooltip: true },
  { prop: 'cron', label: t('schedules.cron'), width: 160 },
  { prop: 'routeStrategy', label: t('schedules.routeStrategy'), width: 120 },
  { prop: 'createdBy', label: t('common.createdBy'), width: 120, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 80 },
  { prop: 'updatedAt', label: t('common.updatedAt'), width: 170, showOverflowTooltip: true },
])

const logColumns = computed(() => [
  { prop: 'triggerType', label: t('schedules.triggerType'), width: 80 },
  { prop: 'executorAddress', label: t('schedules.executorAddress'), width: 160 },
  { prop: 'routeStrategy', label: t('schedules.routeStrategy'), width: 100 },
  { prop: 'status', label: t('schedules.logStatus'), width: 80 },
  { prop: 'costMs', label: t('schedules.costMs'), width: 80 },
  { prop: 'errorMessage', label: t('schedules.errorMessage'), minWidth: 150, showOverflowTooltip: true },
  { prop: 'triggeredAt', label: t('schedules.triggerTime'), width: 170 },
])

const { options: routeStrategyOptions } = useDict('route_strategy')

const list = ref<ScheduleVO[]>([])
const total = ref(0)
const query = reactive({ appCode: undefined as string | undefined, keyword: undefined as string | undefined, status: undefined as number | undefined, page: 1, size: 20 })
const modules = ref<AppOption[]>([])

const stats = computed(() => {
  const total = list.value.length
  const enabled = list.value.filter(i => i.status === 1).length
  const disabled = total - enabled
  return { total, enabled, disabled }
})

// 弹窗
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<any>(null)
const form = reactive({ chainCode: '', chainName: '', cron: '', routeStrategy: 'round_robin', remark: '' })
const chainOptions = ref<ChainVO[]>([])

const rules: Record<string, any[]> = {
  chainCode: [{ required: true, message: t('schedules.selectChain'), trigger: 'change' }],
  cron: [{ required: true, message: t('validation.required', { field: t('schedules.cron') }), trigger: 'blur' }],
}

// 日志弹窗
const logDialogVisible = ref(false)
const currentSchedule = ref<ScheduleVO | null>(null)
const logList = ref<ScheduleLogVO[]>([])
const logTotal = ref(0)
const logQuery = reactive({ scheduleId: undefined as number | undefined, page: 1, size: 10 })

function statusTagType(status: number): string {
  return status === 0 ? 'warning' : status === 1 ? 'success' : 'danger'
}
function statusText(status: number): string {
  return status === 0 ? t('schedules.running') : status === 1 ? t('schedules.success') : t('schedules.failed')
}

async function fetchList() {
  const res = await scheduleApi.list({ keyword: query.keyword, status: query.status, page: query.page, size: query.size })
  list.value = res.records || []
  total.value = res.total || 0
}

async function search() { query.page = 1; fetchList() }
function resetSearch() { query.appCode = undefined; query.keyword = undefined; query.status = undefined; query.page = 1; fetchList() }

async function fetchModules() {
  try {
    modules.value = await executorApi.listApps()
    const code = syncFromApps(modules.value)
    if (code) {
      query.appCode = code
    }
  } catch { modules.value = [] }
}

function onAppFilterChange(code: string | undefined) {
  if (code) {
    setCurrentAppCode(code)
  }
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

function onModuleChangeForChain(appCode: string) {
  form.chainCode = ''
  form.chainName = ''
  fetchChains(appCode)
}

function onChainSelect(code: string) {
  const c = chainOptions.value.find(x => x.code === code)
  form.chainName = c?.name || ''
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
      const dto: ScheduleUpdateDTO = { cron: form.cron, routeStrategy: form.routeStrategy, remark: form.remark }
      await scheduleApi.update(editingId.value, dto)
    } else {
      const dto: ScheduleCreateDTO = { chainCode: form.chainCode, chainName: form.chainName, cron: form.cron, routeStrategy: form.routeStrategy, remark: form.remark }
      await scheduleApi.create(dto)
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
  await scheduleApi.trigger(row.id)
  ElMessage.success(t('schedules.triggerSuccess'))
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
  await fetchModules()
  await fetchList()
})
</script>

<style scoped>
.page-header h2 { margin: 0; font-size: 20px; }
.filter-card { margin-bottom: 16px; }
.stats-card { margin-bottom: 16px; }
.stats-row { display: flex; gap: 24px; align-items: center; flex-wrap: wrap; }
.stat-item b { font-size: 16px; }
.stat-item.total b { color: #409eff; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
