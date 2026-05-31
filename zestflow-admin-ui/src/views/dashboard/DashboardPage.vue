<template>
  <div class="dashboard">
    <h2>{{ $t('dashboard.title') }}</h2>

    <!-- 执行器概览 -->
    <h3 class="section-title">执行器</h3>
    <el-row :gutter="20" class="cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.totalExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item card-success">
            <div class="card-value">{{ stats.healthyExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.healthyExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item card-danger">
            <div class="card-value">{{ stats.errorExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.errorExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item card-warning">
            <div class="card-value">{{ stats.offlineExecutors }}</div>
            <div class="card-label">{{ $t('dashboard.offlineExecutors') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 模块 & 链 & 设计概览 -->
    <h3 class="section-title">{{ $t('dashboard.modules') }}</h3>
    <el-row :gutter="20" class="cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalModules }}</div>
            <div class="card-label">{{ $t('dashboard.totalModules') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.totalChains }}</div>
            <div class="card-label">{{ $t('dashboard.totalChains') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.enabledChains }}</div>
            <div class="card-label">{{ $t('dashboard.enabledChains') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
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
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ stats.todayExecutions }}</div>
            <div class="card-label">{{ $t('dashboard.todayExecutions') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="card-item">
            <div class="card-value">{{ formatMs(stats.avgExecutionMs) }}</div>
            <div class="card-label">{{ $t('dashboard.avgExecutionMs') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
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
    <el-table
      :data="recentExecutions"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
      stripe
      empty-text="暂无执行记录"
    >
      <el-table-column prop="executionId" :label="$t('logs.executionId')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="chainName" :label="$t('logs.chainName')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="executorId" :label="$t('logs.executorId')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="$t('schedules.logStatus')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 0 ? 'danger' : 'info'" size="small">
            {{ row.status === 1 ? $t('schedules.success') : row.status === 0 ? $t('schedules.failed') : '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('schedules.costMs')" width="110">
        <template #default="{ row }">
          {{ row.costMs != null ? row.costMs + ' ms' : '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.timestamp')" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import { queryExecutionTraces } from '@/api/logs'
import type { DashboardStatsVO } from '@/api/dashboard'

const stats = ref<DashboardStatsVO>({
  totalModules: 0,
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

onMounted(() => {
  fetchStats()
  fetchRecentExecutions()
})
</script>

<style scoped>
.dashboard h2 {
  margin: 0 0 24px;
  font-size: 22px;
  color: #303133;
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
</style>
