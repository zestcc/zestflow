<template>
  <div class="logs-page">
    <div class="page-header">
      <h2>{{ $t('logs.executionTitle') }}</h2>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="default">
        <el-form-item :label="$t('logs.executionId')">
          <el-input v-model="query.executionId" :placeholder="$t('logs.executionId')" clearable style="width:160px" />
        </el-form-item>
        <el-form-item :label="$t('logs.appName')">
          <el-input v-model="query.appName" :placeholder="$t('logs.appName')" clearable style="width:140px" />
        </el-form-item>
        <el-form-item :label="$t('logs.keyword')">
          <el-input v-model="query.keyword" :placeholder="$t('logs.keyword')" clearable style="width:160px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="$t('common.status')">
          <el-select v-model="query.status" :placeholder="$t('common.status')" clearable style="width:100px">
            <el-option :label="$t('common.all')" :value="undefined" />
            <el-option :label="$t('logs.success')" :value="1" />
            <el-option :label="$t('logs.failure')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('logs.eventType')">
          <el-select v-model="query.eventTypes" :placeholder="$t('logs.allTypes')" clearable multiple collapse-tags style="width:160px">
            <el-option label="CHAIN_STARTED" value="CHAIN_STARTED" />
            <el-option label="CHAIN_COMPLETED" value="CHAIN_COMPLETED" />
            <el-option label="CHAIN_FAILED" value="CHAIN_FAILED" />
            <el-option label="CHAIN_TIMEOUT" value="CHAIN_TIMEOUT" />
            <el-option label="NODE_STARTED" value="NODE_STARTED" />
            <el-option label="NODE_COMPLETED" value="NODE_COMPLETED" />
            <el-option label="NODE_FAILED" value="NODE_FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('logs.startTime')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            :shortcuts="timeShortcuts"
            range-separator="-"
            :start-placeholder="$t('logs.startTime')"
            :end-placeholder="$t('logs.endTime')"
            style="width:320px"
            @change="onTimeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ $t('logs.search') }}</el-button>
          <el-button @click="resetSearch">{{ $t('logs.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table
        :data="list"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
        stripe
        @row-click="showDetail"
      >
        <el-table-column prop="executionId" :label="$t('logs.executionId')" width="200" show-overflow-tooltip />
        <el-table-column prop="chainName" :label="$t('logs.chainName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="appName" :label="$t('logs.appName')" width="120" show-overflow-tooltip />
        <el-table-column :label="$t('logs.nodeCount')" width="80" align="center">
          <template #default="{ row }">
            <span>{{ row.nodeCount || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('logs.successCount')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.successCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('logs.failedCount')" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="danger" v-if="(row.failedCount || 0) > 0">{{ row.failedCount }}</el-tag>
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('logs.costMs')" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.costMs != null ? row.costMs + 'ms' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('logs.status')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? $t('logs.success') : $t('logs.failure') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('logs.timestamp')" width="170">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="110" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="primary" class="action-btn" @click.stop="showDetail(row)">
              {{ $t('logs.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 0"
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchList"
      />
      <el-empty v-if="total === 0 && loaded" :description="$t('logs.noData')" />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('logs.traceDetail')"
      :size="720"
      :close-on-click-modal="false"
    >
      <template v-if="traceDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('logs.executionId')" :span="2">{{ traceDetail.executionId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.chainName')">{{ traceDetail.chainName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.executorId')">{{ traceDetail.executorId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.appCode')">{{ traceDetail.appCode || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.appName')">{{ traceDetail.appName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.costMs')">{{ traceDetail.costMs ? traceDetail.costMs + 'ms' : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.status')">
            <el-tag :type="traceDetail.status === 1 ? 'success' : 'danger'" size="small">
              {{ traceDetail.status === 1 ? $t('logs.success') : $t('logs.failure') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.nodeCount')">{{ traceDetail.nodeCount || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.successCount')">
            <el-tag size="small" type="success">{{ traceDetail.successCount || 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.failedCount')">
            <el-tag size="small" type="danger" v-if="(traceDetail.failedCount || 0) > 0">{{ traceDetail.failedCount }}</el-tag>
            <span v-else>0</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.errorMessage')" :span="2" v-if="traceDetail.errorMessage">
            <span style="color:var(--el-color-danger)">{{ traceDetail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin:20px 0 12px">{{ $t('logs.detail') }}</h4>
        <el-table
          :data="traceDetail.events || []"
          :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
          stripe
          size="small"
        >
          <el-table-column :label="$t('logs.eventType')" width="140">
            <template #default="{ row }">
              <el-tag :type="eventTagType(row.eventType)" size="small">{{ row.eventType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.nodeName')" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.nodeName || '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.costMs')" width="80" align="center">
            <template #default="{ row }">
              {{ row.costMs != null ? row.costMs + 'ms' : '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.params')" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.params" style="font-family:monospace;font-size:12px;white-space:pre-wrap;word-break:break-all;max-height:60px;overflow-y:auto;display:inline-block">{{ row.params }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.result')" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.result" style="font-family:monospace;font-size:12px;white-space:pre-wrap;word-break:break-all;max-height:60px;overflow-y:auto;display:inline-block">{{ row.result }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.errorMessage')" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.errorMessage" style="color:var(--el-color-danger)">{{ row.errorMessage }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('logs.timestamp')" width="160">
            <template #default="{ row }">
              {{ formatTime(row.timestamp) }}
            </template>
          </el-table-column>
        </el-table>
      </template>
      <div v-else-if="detailLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import type { EventQueryParams, ExecutionTrace } from '@/api/logs'
import { queryExecutionTraces, getExecutionTrace } from '@/api/logs'

const { t } = useI18n()

const query = reactive<EventQueryParams>({
  executionId: undefined,
  appName: undefined,
  keyword: undefined,
  status: undefined,
  eventTypes: undefined,
  startTime: undefined,
  endTime: undefined,
  page: 1,
  pageSize: 20,
})
const timeRange = ref<[Date, Date] | null>(null)
const timeShortcuts = [
  { text: t('common.last15minutes'), value: () => [new Date(Date.now() - 15 * 60 * 1000), new Date()] },
  { text: t('common.last1hour'), value: () => [new Date(Date.now() - 60 * 60 * 1000), new Date()] },
  { text: t('common.last6hours'), value: () => [new Date(Date.now() - 6 * 60 * 60 * 1000), new Date()] },
  { text: t('common.last24hours'), value: () => [new Date(Date.now() - 24 * 60 * 60 * 1000), new Date()] },
]

const list = ref<ExecutionTrace[]>([])
const total = ref(0)
const loaded = ref(false)

// 详情抽屉
const detailVisible = ref(false)
const detailLoading = ref(false)
const traceDetail = ref<ExecutionTrace | null>(null)

function onTimeChange(val: [Date, Date] | null) {
  if (val) {
    query.startTime = val[0].getTime()
    query.endTime = val[1].getTime()
  } else {
    query.startTime = undefined
    query.endTime = undefined
  }
}

function eventTagType(eventType: string): string {
  if (eventType.startsWith('NODE_FAILED') || eventType.startsWith('CHAIN_FAILED') || eventType.startsWith('CHAIN_TIMEOUT')) return 'danger'
  if (eventType.startsWith('CHAIN_STARTED') || eventType.startsWith('NODE_STARTED')) return ''
  return 'success'
}

function formatTime(ts: number | string | undefined): string {
  if (ts == null) return '-'
  const d = typeof ts === 'number' ? new Date(ts) : new Date(ts)
  if (isNaN(d.getTime())) return String(ts)
  return d.toLocaleString()
}

async function fetchList() {
  try {
    const res: any = await queryExecutionTraces(query)
    list.value = res.list || []
    total.value = res.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loaded.value = true
  }
}

function search() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  query.executionId = undefined
  query.appName = undefined
  query.keyword = undefined
  query.status = undefined
  query.eventTypes = undefined
  query.startTime = undefined
  query.endTime = undefined
  timeRange.value = null
  query.page = 1
  fetchList()
}

async function showDetail(row: ExecutionTrace) {
  detailVisible.value = true
  detailLoading.value = true
  traceDetail.value = null
  try {
    if (row.events && row.events.length > 0) {
      traceDetail.value = row
      return
    }
    const res: any = await getExecutionTrace(row.executionId)
    traceDetail.value = res
  } catch {
    traceDetail.value = row
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-card { margin-bottom: 16px; }
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
