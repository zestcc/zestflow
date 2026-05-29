<template>
  <div class="schedules-page">
    <div class="page-header">
      <h2>{{ $t('schedules.title') }}</h2>
      <el-button type="primary" @click="showCreate">{{ $t('schedules.create') }}</el-button>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item :label="$t('schedules.keyword')">
          <el-input v-model="query.keyword" :placeholder="$t('schedules.keyword')" clearable />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-select v-model="query.status" :placeholder="$t('common.status')" clearable style="width:120px">
            <el-option :label="$t('schedules.total')" :value="undefined" />
            <el-option :label="$t('schedules.enabled')" :value="1" />
            <el-option :label="$t('schedules.disabled')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
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
      <el-table :data="list" :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}" stripe>
        <el-table-column prop="chainCode" :label="$t('schedules.chainCode')" show-overflow-tooltip />
        <el-table-column prop="chainName" :label="$t('schedules.chainName')" show-overflow-tooltip />
        <el-table-column prop="cron" :label="$t('schedules.cron')" width="160" />
        <el-table-column :label="$t('schedules.routeStrategy')" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ $t('schedules.' + (row.routeStrategy || 'round_robin')) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" show-overflow-tooltip />
        <el-table-column :label="$t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? $t('schedules.enabled') : $t('schedules.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" />
        <el-table-column :label="$t('common.actions')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" @click="showEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button text size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? $t('schedules.disable') : $t('schedules.enable') }}
            </el-button>
            <el-button text size="small" type="success" @click="handleTrigger(row)">{{ $t('schedules.manualTrigger') }}</el-button>
            <el-button text size="small" type="primary" @click="showLogs(row)">{{ $t('schedules.viewLogs') }}</el-button>
            <el-button text size="small" type="danger" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchList"
      />
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('schedules.edit') : $t('schedules.create')"
      :width="640"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item :label="$t('schedules.chainCode')" prop="chainId">
          <el-select v-model="form.chainId" :placeholder="$t('schedules.selectChain')" filterable style="width:100%">
            <el-option
              v-for="c in chainOptions"
              :key="c.id"
              :label="c.code + ' - ' + c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('schedules.cron')" prop="cron">
          <el-input v-model="form.cron" placeholder="0 0/5 * * * ?" />
        </el-form-item>
        <el-form-item :label="$t('schedules.routeStrategy')">
          <el-select v-model="form.routeStrategy" style="width:200px">
            <el-option label="Round Robin" value="round_robin" />
            <el-option label="Hash" value="hash" />
            <el-option label="Random" value="random" />
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
      <el-table :data="logList" :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}" stripe>
        <el-table-column :label="$t('schedules.triggerType')" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.triggerType === 'cron' ? '' : 'warning'">
              {{ row.triggerType === 'cron' ? $t('schedules.cronTrigger') : $t('schedules.manual') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="executorAddress" :label="$t('schedules.executorAddress')" width="160" />
        <el-table-column prop="routeStrategy" :label="$t('schedules.routeStrategy')" width="100">
          <template #default="{ row }">
            {{ row.routeStrategy ? $t('schedules.' + row.routeStrategy) : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('schedules.logStatus')" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" :label="$t('schedules.costMs')" width="80" />
        <el-table-column prop="errorMessage" :label="$t('schedules.errorMessage')" show-overflow-tooltip min-width="150" />
        <el-table-column prop="triggeredAt" :label="$t('schedules.triggerTime')" width="170" />
      </el-table>
      <el-pagination
        v-if="logTotal > 0"
        v-model:current-page="logQuery.page"
        v-model:page-size="logQuery.size"
        :total="logTotal"
        layout="total, sizes, prev, pager, next"
        @change="fetchLogs"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scheduleApi, type ScheduleVO, type ScheduleCreateDTO, type ScheduleUpdateDTO, type ScheduleLogVO } from '@/api/schedule'
import { chainApi, type ChainVO } from '@/api/chain'

const { t } = useI18n()

const list = ref<ScheduleVO[]>([])
const total = ref(0)
const query = reactive({ keyword: undefined as string | undefined, status: undefined as number | undefined, page: 1, size: 20 })

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
const form = reactive<ScheduleCreateDTO & { chainId: number }>({ chainId: 0, cron: '', routeStrategy: 'round_robin', remark: '' })
const chainOptions = ref<ChainVO[]>([])

const rules = {
  chainId: [{ required: true, message: t('schedules.selectChain'), trigger: 'change' }],
  cron: [{ required: true, message: t('schedules.cron') + t('validation.required', { field: '' }), trigger: 'blur' }],
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
function resetSearch() { query.keyword = undefined; query.status = undefined; query.page = 1; fetchList() }

async function fetchChains() {
  try {
    const res = await chainApi.list({ moduleId: 0, page: 1, size: 999 })
    chainOptions.value = res.records || []
  } catch (_e) { chainOptions.value = [] }
}

function resetForm() {
  form.chainId = 0
  form.cron = ''
  form.routeStrategy = 'round_robin'
  form.remark = ''
}

async function showCreate() {
  isEditing.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  fetchChains()
}

async function showEdit(row: ScheduleVO) {
  isEditing.value = true
  editingId.value = row.id
  form.chainId = row.chainId
  form.cron = row.cron
  form.routeStrategy = row.routeStrategy || 'round_robin'
  form.remark = row.remark || ''
  dialogVisible.value = true
  fetchChains()
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
      const dto: ScheduleCreateDTO = { chainId: form.chainId, cron: form.cron, routeStrategy: form.routeStrategy, remark: form.remark }
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

onMounted(fetchList)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-card { margin-bottom: 16px; }
.stats-card { margin-bottom: 16px; }
.stats-row { display: flex; gap: 24px; align-items: center; }
.stat-item b { font-size: 16px; }
.stat-item.total b { color: #409eff; }
</style>
