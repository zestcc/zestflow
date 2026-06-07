<template>
  <div class="dashboard">
    <div v-if="features?.admin" class="runtime-strip">
      <span class="runtime-strip-label">{{ $t('dashboard.runtimeTitle') }}</span>
      <div class="runtime-tags">
        <span class="runtime-chip">{{ $t('dashboard.deployMode') }} · {{ features.admin.deployMode }}</span>
        <span class="runtime-chip">{{ $t('dashboard.cacheType') }} · {{ features.admin.cacheType }}</span>
        <span class="runtime-chip" :class="features.admin.redisRequired ? 'runtime-chip--warn' : 'runtime-chip--ok'">
          {{ features.admin.redisRequired ? $t('dashboard.redisRequired') : $t('dashboard.redisNotRequired') }}
        </span>
        <span v-if="features.security" class="runtime-chip" :class="features.security.registryTokenConfigured ? 'runtime-chip--ok' : ''">
          {{ $t('dashboard.registryToken') }}:
          {{ features.security.registryTokenConfigured ? $t('dashboard.registryTokenOn') : $t('dashboard.registryTokenOff') }}
        </span>
        <span v-if="features.security?.executorAccessTokenConfigured != null" class="runtime-chip"
          :class="features.security.executorAccessTokenConfigured ? 'runtime-chip--ok' : ''">
          {{ $t('dashboard.executorAccessToken') }}:
          {{ features.security.executorAccessTokenConfigured ? $t('dashboard.registryTokenOn') : $t('dashboard.registryTokenOff') }}
        </span>
      </div>
    </div>

    <!-- 执行器概览 -->
    <h3 class="section-title">执行器</h3>
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-card-value">{{ stats.totalExecutors }}</div>
          <div class="stat-card-label">{{ $t('dashboard.totalExecutors') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card--success">
          <div class="stat-card-value">{{ stats.healthyExecutors }}</div>
          <div class="stat-card-label">{{ $t('dashboard.healthyExecutors') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card--danger">
          <div class="stat-card-value">{{ stats.errorExecutors }}</div>
          <div class="stat-card-label">{{ $t('dashboard.errorExecutors') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card stat-card--warning">
          <div class="stat-card-value">{{ stats.offlineExecutors }}</div>
          <div class="stat-card-label">{{ $t('dashboard.offlineExecutors') }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 应用 & 链 & 设计概览 -->
    <h3 class="section-title">{{ $t('dashboard.apps') }}</h3>
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-card-value">{{ stats.totalApps }}</div>
          <div class="stat-card-label">{{ $t('dashboard.totalApps') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-card-value">{{ stats.totalChains }}</div>
          <div class="stat-card-label">{{ $t('dashboard.totalChains') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-card-value">{{ stats.enabledChains }}</div>
          <div class="stat-card-label">{{ $t('dashboard.enabledChains') }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="stat-card">
          <div class="stat-card-value">{{ stats.totalDesigns }}</div>
          <div class="stat-card-label">{{ $t('dashboard.totalDesigns') }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 执行统计 -->
    <h3 class="section-title">{{ $t('dashboard.executionStats') }}</h3>
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="24" :sm="8" :md="8">
        <div class="stat-card stat-card--wide">
          <div class="stat-card-value">{{ stats.todayExecutions }}</div>
          <div class="stat-card-label">{{ $t('dashboard.todayExecutions') }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <div class="stat-card stat-card--wide">
          <div class="stat-card-value">{{ formatMs(stats.avgExecutionMs) }}</div>
          <div class="stat-card-label">{{ $t('dashboard.avgExecutionMs') }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <div class="stat-card stat-card--wide" :class="stats.successRate >= 80 ? 'stat-card--success' : 'stat-card--danger'">
          <div class="stat-card-value">{{ formatRate(stats.successRate) }}</div>
          <div class="stat-card-label">{{ $t('dashboard.successRate') }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 最近执行记录 -->
    <h3 class="section-title">{{ $t('dashboard.recentExecutions') }}</h3>
    <div class="table-panel">
    <ResponsiveTable
      :data="recentExecutions"
      :columns="executionColumns"
      :row-key="'executionId'"
      empty-text="暂无执行记录"
    >
      <template #executionId="{ row }">
        {{ row.executionId }}
      </template>
      <template #chainName="{ row }">
        {{ row.chainName || '-' }}
      </template>
      <template #executorId="{ row }">
        {{ row.executorId || '-' }}
      </template>
      <template #status="{ row }">
        <el-tag :type="scheduleLogTagType(row.status)" size="small">
          {{ scheduleLogLabel(row.status) }}
        </el-tag>
      </template>
      <template #costMs="{ row }">
        {{ row.costMs != null ? row.costMs + ' ms' : '-' }}
      </template>
      <template #startTime="{ row }">
        {{ formatTime(row.startTime) }}
      </template>
    </ResponsiveTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { dashboardApi } from '@/api/dashboard'
import { queryExecutionTraces } from '@/api/logs'
import { getFeatures, type Features } from '@/api/system'
import type { DashboardStatsVO } from '@/api/dashboard'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useDictLabel } from '@/composables/useDictLabel'

const { t } = useI18n()
const { labelOf: scheduleLogLabel, tagTypeOf: scheduleLogTagType } = useDictLabel('schedule_log_status')

const features = ref<Features | null>(null)

const stats = ref<DashboardStatsVO>({
  totalApps: 0,
  totalExecutors: 0,
  healthyExecutors: 0,
  errorExecutors: 0,
  offlineExecutors: 0,
  totalChains: 0,
  enabledChains: 0,
  totalDesigns: 0,
  todayExecutions: 0,
  avgExecutionMs: 0,
  successRate: 0,
})

interface ExecutionRecord {
  executionId: string
  chainName: string
  executorId: string
  status: number | null
  costMs: number | null
  startTime: number
}

const recentExecutions = ref<ExecutionRecord[]>([])

function formatMs(ms: number): string {
  if (ms <= 0) return '-'
  return ms < 1000 ? ms.toFixed(1) + ' ms' : (ms / 1000).toFixed(2) + ' s'
}

function formatRate(rate: number): string {
  return rate.toFixed(1) + '%'
}

function formatTime(ts: number): string {
  if (!ts) return '-'
  const d = new Date(ts)
  return d.toLocaleString()
}

const executionColumns = computed(() => [
  { prop: 'executionId', label: t('logs.executionId'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'chainName', label: t('logs.chainName'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'executorId', label: t('logs.executorId'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'status', label: t('schedules.logStatus'), width: 100 },
  { prop: 'costMs', label: t('schedules.costMs'), width: 110 },
  { prop: 'startTime', label: t('logs.timestamp'), width: 180 },
])

async function fetchStats() {
  try {
    stats.value = await dashboardApi.stats()
  } catch {}
}

async function fetchRecentExecutions() {
  try {
    const res: any = await queryExecutionTraces({ page: 1, pageSize: 5 })
    recentExecutions.value = (res.list || []).map((r: any) => ({
      executionId: r.executionId,
      chainName: r.chainName,
      executorId: r.executorId,
      status: r.status,
      costMs: r.costMs,
      startTime: r.startTime,
    }))
  } catch {}
}

async function fetchFeatures() {
  try {
    features.value = await getFeatures()
  } catch {}
}

onMounted(() => {
  fetchFeatures()
  fetchStats()
  fetchRecentExecutions()
})
</script>

<style scoped>
.dashboard {
  max-width: 1440px;
}

.runtime-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: var(--surface-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.runtime-strip-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary);
  white-space: nowrap;
}

.runtime-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.runtime-chip {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-secondary);
  background: #f0f2f5;
  border: 1px solid transparent;
}

.runtime-chip--ok {
  color: #3a7a2a;
  background: #f0f9eb;
}

.runtime-chip--warn {
  color: #b88230;
  background: #fdf6ec;
}

.section-title {
  margin: 20px 0 12px;
  font-size: 15px;
  color: var(--text-secondary);
  font-weight: 600;
}

.stat-row {
  margin-bottom: 4px;
}

.stat-row :deep(.el-col) {
  margin-bottom: 12px;
}

.stat-card {
  background: var(--surface-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 18px 16px 16px;
  text-align: center;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.stat-card:hover {
  border-color: #d4dbe6;
  box-shadow: var(--shadow-md);
}

.stat-card--wide {
  padding-top: 20px;
  padding-bottom: 18px;
}

.stat-card-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  color: #2563eb;
  letter-spacing: -0.02em;
}

.stat-card--success .stat-card-value {
  color: #16a34a;
}

.stat-card--danger .stat-card-value {
  color: #dc2626;
}

.stat-card--warning .stat-card-value {
  color: #d97706;
}

.stat-card-label {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 6px;
}

.table-panel {
  background: var(--surface-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 4px 4px 8px;
}

@media (max-width: 767px) {
  .runtime-strip {
    padding: 10px 12px;
  }

  .section-title {
    margin: 16px 0 10px;
    font-size: 14px;
  }

  .stat-card {
    padding: 14px 12px;
  }

  .stat-card-value {
    font-size: 22px;
  }

  .stat-card-label {
    font-size: 12px;
    margin-top: 4px;
  }
}
</style>
