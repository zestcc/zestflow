<template>
  <div class="dashboard">
    <el-alert v-if="features?.admin" type="info" :closable="false" class="runtime-banner" show-icon>
      <template #title>{{ $t('dashboard.runtimeTitle') }}</template>
      <div class="runtime-tags">
        <el-tag size="small" effect="plain">{{ $t('dashboard.deployMode') }}: {{ features.admin.deployMode }}</el-tag>
        <el-tag size="small" effect="plain">{{ $t('dashboard.cacheType') }}: {{ features.admin.cacheType }}</el-tag>
        <el-tag size="small" :type="features.admin.redisRequired ? 'warning' : 'success'">
          {{ features.admin.redisRequired ? $t('dashboard.redisRequired') : $t('dashboard.redisNotRequired') }}
        </el-tag>
        <el-tag v-if="features.security" size="small" :type="features.security.registryTokenConfigured ? 'success' : 'info'">
          {{ $t('dashboard.registryToken') }}:
          {{ features.security.registryTokenConfigured ? $t('dashboard.registryTokenOn') : $t('dashboard.registryTokenOff') }}
        </el-tag>
        <el-tag v-if="features.security?.executorAccessTokenConfigured != null" size="small"
          :type="features.security.executorAccessTokenConfigured ? 'success' : 'info'">
          {{ $t('dashboard.executorAccessToken') }}:
          {{ features.security.executorAccessTokenConfigured ? $t('dashboard.registryTokenOn') : $t('dashboard.registryTokenOff') }}
        </el-tag>
      </div>
    </el-alert>

    <!-- 执行器概览 -->
    <h3 class="section-title">执行器</h3>
    <el-row :gutter="20" class="cards">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.totalExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item card-success">
            <div class="card-value">{{ stats.healthyExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.healthyExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item card-danger">
            <div class="card-value">{{ stats.errorExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.errorExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item card-warning">
            <div class="card-value">{{ stats.offlineExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.offlineExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 应用 & 链 & 设计概览 -->
    <h3 class="section-title">{{ $t('dashboard.apps') }}</h3>
    <el-row :gutter="20" class="cards">
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalApps }}</div>
            <div class="card-label">{{ $t('dashboard.totalApps') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalChains }}</div>
            <div class="card-label">{{ $t('dashboard.totalChains') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.enabledChains }}</div>
            <div class="card-label">{{ $t('dashboard.enabledChains') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalDesigns }}</div>
            <div class="card-label">{{ $t('dashboard.totalDesigns') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 执行统计 -->
    <h3 class="section-title">{{ $t('dashboard.executionStats') }}</h3>
    <el-row :gutter="20" class="cards">
      <el-col :xs="24" :sm="8" :md="8">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.todayExecutions }}</div>
            <div class="card-label">{{ $t('dashboard.todayExecutions') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ formatMs(stats.avgExecutionMs) }}</div>
            <div class="card-label">{{ $t('dashboard.avgExecutionMs') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8">
        <el-card shadow="hover">
          <div class="card-item" :class="stats.successRate >= 80 ? 'card-success' : 'card-danger'">
            <div class="card-value">{{ formatRate(stats.successRate) }}</div>
            <div class="card-label">{{ $t('dashboard.successRate') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近执行记录 -->
    <h3 class="section-title">{{ $t('dashboard.recentExecutions') }}</h3>
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
.runtime-banner {
  margin-bottom: 20px;
}

.runtime-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.section-title {
  margin: 24px 0 16px;
  font-size: 16px;
  color: #606266;
  font-weight: 600;
}

.card-item {
  text-align: center;
  padding: 10px 0;
}

.card-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}

.card-success .card-value {
  color: #67c23a;
}

.card-danger .card-value {
  color: #f56c6c;
}

.card-warning .card-value {
  color: #e6a23c;
}

.card-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

@media (max-width: 767px) {
  .section-title {
    margin: 16px 0 12px;
    font-size: 14px;
  }

  .card-value {
    font-size: 24px;
  }

  .card-item {
    padding: 6px 0;
  }

  .card-label {
    font-size: 12px;
    margin-top: 4px;
  }
}
</style>
