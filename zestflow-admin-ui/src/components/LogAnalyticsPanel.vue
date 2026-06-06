<template>
  <div class="log-analytics">
    <div class="analytics-toolbar">
      <el-radio-group v-model="timeRange" size="small" @change="refresh">
        <el-radio-button label="24h">{{ $t('logs.range24h') }}</el-radio-button>
        <el-radio-button label="7d">{{ $t('logs.range7d') }}</el-radio-button>
        <el-radio-button label="30d">{{ $t('logs.range30d') }}</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="executorFilter"
        class="page-filter-control--md"
        :placeholder="$t('logs.executorId')"
        clearable
        @keyup.enter="refresh"
        @clear="refresh"
      />
      <el-button type="primary" size="small" :loading="loading" @click="refresh">{{ $t('logs.search') }}</el-button>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.executionCount }}</div>
          <div class="stat-label">{{ $t('logs.statExecutions') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :class="stats.successRate >= 95 ? 'ok' : 'warn'">{{ formatRate(stats.successRate) }}</div>
          <div class="stat-label">{{ $t('logs.statSuccessRate') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value danger">{{ stats.failCount }}</div>
          <div class="stat-label">{{ $t('logs.statFailures') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ formatMs(stats.avgCostMs) }}</div>
          <div class="stat-label">{{ $t('logs.statAvgCost') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ formatMs(stats.p95CostMs) }}</div>
          <div class="stat-label">{{ $t('logs.statP95') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value">{{ stats.inProgressCount }}</div>
          <div class="stat-label">{{ $t('logs.inProgress') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="chart-card">
      <template #header>
        <span>{{ $t('logs.trendTitle') }}</span>
        <el-radio-group v-model="granularity" size="small" style="float:right" @change="loadTrend">
          <el-radio-button label="hour">{{ $t('logs.granularityHour') }}</el-radio-button>
          <el-radio-button label="day">{{ $t('logs.granularityDay') }}</el-radio-button>
        </el-radio-group>
      </template>
      <div v-if="trend.length === 0" class="empty-hint">{{ $t('logs.noData') }}</div>
      <div v-else class="trend-chart">
        <div v-for="p in trend" :key="p.bucketStart" class="trend-bar-wrap" :title="trendTooltip(p)">
          <div class="trend-bar-stack">
            <div class="bar-success" :style="{ height: barHeight(p.successCount) + '%' }" />
            <div class="bar-fail" :style="{ height: barHeight(p.failCount) + '%' }" />
          </div>
          <span class="trend-label">{{ formatBucket(p.bucketStart) }}</span>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" class="rank-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="rank-header">
              <span>{{ $t('logs.rankChains') }}</span>
              <el-select v-model="chainRankBy" size="small" style="width:120px" @change="loadChainRank">
                <el-option :label="$t('logs.rankByCount')" value="count" />
                <el-option :label="$t('logs.rankByFail')" value="fail" />
                <el-option :label="$t('logs.rankBySlow')" value="slow" />
              </el-select>
            </div>
          </template>
          <el-table :data="chainRank" size="small" max-height="280" stripe>
            <el-table-column prop="name" :label="$t('logs.chainCode')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="totalCount" :label="$t('logs.statExecutions')" width="80" align="center" />
            <el-table-column prop="failCount" :label="$t('logs.statFailures')" width="70" align="center" />
            <el-table-column :label="$t('logs.statSuccessRate')" width="90" align="center">
              <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
            </el-table-column>
            <el-table-column :label="$t('logs.costMs')" width="90" align="center">
              <template #default="{ row }">{{ Math.round(row.avgCostMs) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="rank-header">
              <span>{{ $t('logs.rankExecutors') }}</span>
              <el-select v-model="executorRankBy" size="small" style="width:120px" @change="loadExecutorRank">
                <el-option :label="$t('logs.rankByCount')" value="count" />
                <el-option :label="$t('logs.rankByFail')" value="fail" />
                <el-option :label="$t('logs.rankBySlow')" value="slow" />
              </el-select>
            </div>
          </template>
          <el-table :data="executorRank" size="small" max-height="280" stripe>
            <el-table-column prop="key" :label="$t('logs.executorId')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="totalCount" :label="$t('logs.statExecutions')" width="80" align="center" />
            <el-table-column prop="failCount" :label="$t('logs.statFailures')" width="70" align="center" />
            <el-table-column :label="$t('logs.statSuccessRate')" width="90" align="center">
              <template #default="{ row }">{{ formatRate(row.successRate) }}</template>
            </el-table-column>
            <el-table-column :label="$t('logs.costMs')" width="90" align="center">
              <template #default="{ row }">{{ Math.round(row.avgCostMs) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" class="rank-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>{{ $t('logs.rankNodes') }}</template>
          <el-table :data="nodeRank" size="small" max-height="240" stripe>
            <el-table-column prop="name" :label="$t('logs.nodeName')" min-width="120" show-overflow-tooltip />
            <el-table-column prop="totalCount" :label="$t('logs.eventType')" width="80" align="center" />
            <el-table-column prop="failCount" :label="$t('logs.statFailures')" width="70" align="center" />
            <el-table-column :label="$t('logs.costMs')" width="90" align="center">
              <template #default="{ row }">{{ Math.round(row.avgCostMs) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>{{ $t('logs.failureClusters') }}</template>
          <el-table :data="failures" size="small" max-height="240" stripe>
            <el-table-column prop="errorSummary" :label="$t('logs.errorMessage')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="count" :label="$t('logs.statExecutions')" width="70" align="center" />
            <el-table-column :label="$t('logs.timestamp')" width="150">
              <template #default="{ row }">{{ formatTime(row.lastSeen) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import type {
  EventStats,
  ExecutionRankItem,
  ExecutionTrendPoint,
  FailureClusterItem,
  LogAnalyticsParams,
} from '@/api/logs'
import {
  queryLogStats,
  queryLogTrend,
  queryChainRanking,
  queryExecutorRanking,
  queryNodeRanking,
  queryFailureClusters,
} from '@/api/logs'

const props = defineProps<{
  appCode?: string
}>()

const timeRange = ref<'24h' | '7d' | '30d'>('24h')
const granularity = ref<'hour' | 'day'>('hour')
const executorFilter = ref('')
const chainRankBy = ref<'count' | 'fail' | 'slow'>('count')
const executorRankBy = ref<'count' | 'fail' | 'slow'>('count')
const loading = ref(false)

const stats = reactive<EventStats>({
  totalCount: 0,
  executionCount: 0,
  successCount: 0,
  inProgressCount: 0,
  successRate: 0,
  avgCostMs: 0,
  p95CostMs: 0,
  maxCostMs: 0,
  failCount: 0,
})

const trend = ref<ExecutionTrendPoint[]>([])
const chainRank = ref<ExecutionRankItem[]>([])
const executorRank = ref<ExecutionRankItem[]>([])
const nodeRank = ref<ExecutionRankItem[]>([])
const failures = ref<FailureClusterItem[]>([])

const trendMax = computed(() => {
  let max = 1
  for (const p of trend.value) {
    max = Math.max(max, p.totalCount)
  }
  return max
})

function buildParams(extra?: Partial<LogAnalyticsParams>): LogAnalyticsParams {
  const now = Date.now()
  const hours = timeRange.value === '30d' ? 720 : timeRange.value === '7d' ? 168 : 24
  return {
    appCode: props.appCode,
    executorId: executorFilter.value || undefined,
    startTime: now - hours * 3600_000,
    endTime: now,
    granularity: granularity.value,
    limit: 10,
    ...extra,
  }
}

function formatRate(v: number) {
  if (v == null || Number.isNaN(v)) return '-'
  return `${v.toFixed(1)}%`
}

function formatMs(v: number) {
  if (v == null || v <= 0) return '-'
  if (v >= 1000) return `${(v / 1000).toFixed(1)}s`
  return `${Math.round(v)}ms`
}

function formatTime(ts: number) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

function formatBucket(ts: number) {
  const d = new Date(ts)
  if (granularity.value === 'day') {
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  return `${d.getHours()}:00`
}

function barHeight(count: number) {
  return Math.max(4, (count / trendMax.value) * 100)
}

function trendTooltip(p: ExecutionTrendPoint) {
  return `${formatBucket(p.bucketStart)}: ${p.totalCount} (${p.successCount} ok / ${p.failCount} fail)`
}

async function loadStats() {
  const res = await queryLogStats(buildParams())
  Object.assign(stats, res || {})
}

async function loadTrend() {
  trend.value = (await queryLogTrend(buildParams({ rankBy: undefined }))) || []
}

async function loadChainRank() {
  chainRank.value = (await queryChainRanking(buildParams({ rankBy: chainRankBy.value }))) || []
}

async function loadExecutorRank() {
  executorRank.value = (await queryExecutorRanking(buildParams({ rankBy: executorRankBy.value }))) || []
}

async function loadNodeRank() {
  nodeRank.value = (await queryNodeRanking(buildParams({ rankBy: 'fail' }))) || []
}

async function loadFailures() {
  failures.value = (await queryFailureClusters(buildParams())) || []
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([
      loadStats(),
      loadTrend(),
      loadChainRank(),
      loadExecutorRank(),
      loadNodeRank(),
      loadFailures(),
    ])
  } finally {
    loading.value = false
  }
}

watch(() => props.appCode, () => refresh(), { immediate: true })

defineExpose({ refresh })
</script>

<style scoped>
.analytics-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}
.stats-row { margin-bottom: 12px; }
.stat-card { text-align: center; }
.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.stat-value.ok { color: #67c23a; }
.stat-value.warn { color: #e6a23c; }
.stat-value.danger { color: #f56c6c; }
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.chart-card { margin-bottom: 12px; }
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 160px;
  overflow-x: auto;
  padding-bottom: 4px;
}
.trend-bar-wrap {
  flex: 1;
  min-width: 28px;
  max-width: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.trend-bar-stack {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column-reverse;
  justify-content: flex-start;
  border-radius: 3px 3px 0 0;
  overflow: hidden;
  background: #f0f2f5;
}
.bar-success { background: #67c23a; min-height: 0; }
.bar-fail { background: #f56c6c; min-height: 0; }
.trend-label {
  font-size: 10px;
  color: #909399;
  margin-top: 4px;
  white-space: nowrap;
}
.rank-row { margin-bottom: 12px; }
.rank-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.empty-hint {
  text-align: center;
  color: #909399;
  padding: 24px;
  font-size: 13px;
}
</style>
