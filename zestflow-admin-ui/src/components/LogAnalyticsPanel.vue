<template>
  <div class="log-analytics">
    <div class="page-toolbar analytics-toolbar">
      <div class="page-filters analytics-toolbar-filters">
        <el-radio-group v-model="timeRange" size="small" @change="refresh">
          <el-radio-button v-for="item in timeRangeOptions" :key="item.value" :label="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <el-select
          v-model="executorFilter"
          class="page-filter-control"
          filterable
          clearable
          :placeholder="$t('logs.filterExecutor')"
          @change="handleExecutorChange"
          @clear="handleExecutorChange"
        >
          <el-option :label="$t('common.all')" value="" />
          <el-option
            v-for="item in executorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select
          v-model="chainFilter"
          class="page-filter-control"
          filterable
          clearable
          :placeholder="$t('logs.filterChain')"
          @change="handleChainChange"
          @clear="handleChainChange"
        >
          <el-option :label="$t('common.all')" value="" />
          <el-option
            v-for="item in chainOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button type="primary" size="small" :loading="loading" @click="refresh">{{ $t('logs.search') }}</el-button>
      </div>
    </div>
    <div v-if="hasActiveFilters" class="active-filters">
      <span class="active-filters-label">{{ $t('logs.activeFilters') }}</span>
      <el-tag v-if="executorFilter" size="small" type="info">{{ $t('logs.filterExecutor') }}: {{ executorFilter }}</el-tag>
      <el-tag v-if="chainFilter" size="small" type="info">{{ $t('logs.filterChain') }}: {{ chainFilterLabel }}</el-tag>
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
        <div class="chart-header">
          <span>{{ $t('logs.trendTitle') }}</span>
          <div class="chart-header-actions">
            <el-radio-group v-model="chartType" size="small">
              <el-radio-button label="line">{{ $t('logs.chartTypeLine') }}</el-radio-button>
              <el-radio-button label="bar">{{ $t('logs.chartTypeBar') }}</el-radio-button>
            </el-radio-group>
            <el-radio-group v-model="granularity" size="small" @change="loadTrend">
              <el-radio-button v-for="item in granularityOptions" :key="item.value" :label="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <div v-if="trend.length === 0" class="empty-hint">{{ $t('logs.noData') }}</div>
      <VChart v-else class="trend-echart" :option="trendChartOption" autoresize />
    </el-card>

    <el-row :gutter="12" class="rank-row">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="rank-header">
              <span>{{ $t('logs.rankChains') }}</span>
              <el-select v-model="chainRankBy" size="small" style="width:120px" @change="loadChainRank">
                <el-option v-for="item in rankByOptions" :key="item.value" :label="item.label" :value="item.value" />
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
                <el-option v-for="item in rankByOptions" :key="item.value" :label="item.label" :value="item.value" />
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
import { useI18n } from 'vue-i18n'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import type { ComposeOption } from 'echarts/core'
import type { LineSeriesOption, BarSeriesOption } from 'echarts/charts'
import type {
  GridComponentOption,
  TooltipComponentOption,
  LegendComponentOption,
} from 'echarts/components'
import VChart from 'vue-echarts'
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
import { useDictLabel } from '@/composables/useDictLabel'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

type TrendChartOption = ComposeOption<
  | LineSeriesOption
  | BarSeriesOption
  | GridComponentOption
  | TooltipComponentOption
  | LegendComponentOption
>

const props = defineProps<{
  appCode?: string
}>()

const { t, locale } = useI18n()
const { options: timeRangeOptions } = useDictLabel('log_analytics_time_range')
const { options: granularityOptions } = useDictLabel('log_analytics_granularity')
const { options: rankByOptions } = useDictLabel('log_analytics_rank_by')

const timeRange = ref<'24h' | '7d' | '30d'>('24h')
const granularity = ref<'hour' | 'day'>('hour')
const executorFilter = ref('')
const chainFilter = ref('')
const executorOptions = ref<Array<{ value: string; label: string }>>([])
const chainOptions = ref<Array<{ value: string; label: string }>>([])
const chainRankBy = ref<'count' | 'fail' | 'slow'>('count')
const executorRankBy = ref<'count' | 'fail' | 'slow'>('count')
const loading = ref(false)

const hasActiveFilters = computed(() => !!executorFilter.value || !!chainFilter.value)

const chainFilterLabel = computed(() => {
  if (!chainFilter.value) return ''
  const hit = chainOptions.value.find((o) => o.value === chainFilter.value)
  return hit?.label || chainFilter.value
})

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

/** 默认折线图，可切换柱状图（ECharts 渲染，数据驱动） */
const chartType = ref<'line' | 'bar'>('line')

const trendChartOption = computed<TrendChartOption>(() => {
  void locale.value
  const data = trend.value
  const isLine = chartType.value === 'line'
  const labelExecutions = t('logs.statExecutions')
  const labelSuccess = t('logs.statSuccessCount')
  const labelFailures = t('logs.statFailures')

  return {
    color: ['#409eff', '#f56c6c', '#67c23a'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: isLine ? 'line' : 'shadow' },
    },
    legend: {
      bottom: 0,
      data: isLine ? [labelExecutions, labelFailures] : [labelSuccess, labelFailures],
    },
    grid: { left: 48, right: 16, top: 24, bottom: 40 },
    xAxis: {
      type: 'category',
      data: data.map((p) => formatBucket(p.bucketStart)),
      boundaryGap: !isLine,
      axisLabel: { color: '#909399', fontSize: 11 },
      axisLine: { lineStyle: { color: '#e4e7ed' } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#909399', fontSize: 11 },
      splitLine: { lineStyle: { color: '#ebeef5' } },
    },
    series: isLine
      ? [
          {
            name: labelExecutions,
            type: 'line',
            smooth: true,
            showSymbol: data.length <= 48,
            areaStyle: { color: 'rgba(64, 158, 255, 0.12)' },
            itemStyle: { color: '#409eff' },
            data: data.map((p) => p.totalCount),
          },
          {
            name: labelFailures,
            type: 'line',
            smooth: true,
            showSymbol: data.length <= 48,
            itemStyle: { color: '#f56c6c' },
            lineStyle: { type: 'dashed', width: 2 },
            data: data.map((p) => p.failCount),
          },
        ]
      : [
          {
            name: labelSuccess,
            type: 'bar',
            stack: 'executions',
            barMaxWidth: 28,
            itemStyle: { color: '#67c23a', borderRadius: [0, 0, 0, 0] },
            data: data.map((p) => p.successCount),
          },
          {
            name: labelFailures,
            type: 'bar',
            stack: 'executions',
            barMaxWidth: 28,
            itemStyle: { color: '#f56c6c', borderRadius: [3, 3, 0, 0] },
            data: data.map((p) => p.failCount),
          },
        ],
  }
})

const DIMENSION_LIMIT = 200

function buildParams(extra?: Partial<LogAnalyticsParams>): LogAnalyticsParams {
  const now = Date.now()
  const hours = timeRange.value === '30d' ? 720 : timeRange.value === '7d' ? 168 : 24
  return {
    appCode: props.appCode,
    executorId: executorFilter.value || undefined,
    chainId: chainFilter.value || undefined,
    startTime: now - hours * 3600_000,
    endTime: now,
    granularity: granularity.value,
    limit: 10,
    ...extra,
  }
}

/** 维度下拉：按当前时间窗从日志聚合结果加载（数据驱动，非固定列表） */
function buildDimensionParams(partial?: Partial<LogAnalyticsParams>): LogAnalyticsParams {
  return buildParams({
    limit: DIMENSION_LIMIT,
    rankBy: 'count',
    granularity: undefined,
    ...partial,
  })
}

function mapExecutorOptions(rows: ExecutionRankItem[]) {
  return (rows || [])
    .filter((row) => row.key)
    .map((row) => ({ value: row.key, label: row.key }))
}

function mapChainOptions(rows: ExecutionRankItem[]) {
  return (rows || [])
    .filter((row) => row.key)
    .map((row) => ({
      value: row.key,
      label: row.name && row.name !== row.key ? `${row.name} (${row.key})` : row.key,
    }))
}

function ensureSelectionStillValid() {
  if (executorFilter.value && !executorOptions.value.some((o) => o.value === executorFilter.value)) {
    executorFilter.value = ''
  }
  if (chainFilter.value && !chainOptions.value.some((o) => o.value === chainFilter.value)) {
    chainFilter.value = ''
  }
}

async function loadExecutorOptions() {
  if (!props.appCode) {
    executorOptions.value = []
    return
  }
  const params = buildDimensionParams({
    executorId: undefined,
    chainId: chainFilter.value || undefined,
  })
  executorOptions.value = mapExecutorOptions(await queryExecutorRanking(params))
}

async function loadChainOptions() {
  if (!props.appCode) {
    chainOptions.value = []
    return
  }
  const params = buildDimensionParams({
    chainId: undefined,
    executorId: executorFilter.value || undefined,
  })
  chainOptions.value = mapChainOptions(await queryChainRanking(params))
}

async function loadFilterOptions() {
  await Promise.all([loadExecutorOptions(), loadChainOptions()])
  ensureSelectionStillValid()
}

async function handleExecutorChange() {
  await loadChainOptions()
  ensureSelectionStillValid()
  await refresh()
}

async function handleChainChange() {
  await loadExecutorOptions()
  ensureSelectionStillValid()
  await refresh()
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
      loadFilterOptions(),
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

watch(() => props.appCode, async () => {
  executorFilter.value = ''
  chainFilter.value = ''
  await loadFilterOptions()
  await refresh()
}, { immediate: true })

watch(timeRange, async () => {
  await loadFilterOptions()
})

defineExpose({ refresh })
</script>

<style scoped>
.analytics-toolbar {
  margin-bottom: 16px;
}
.analytics-toolbar-filters {
  flex-wrap: nowrap;
  overflow-x: auto;
}
.active-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  color: #606266;
}
.active-filters-label {
  color: #909399;
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
.chart-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.chart-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.trend-echart {
  width: 100%;
  height: 240px;
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
