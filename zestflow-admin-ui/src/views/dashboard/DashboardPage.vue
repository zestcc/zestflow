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

    <!-- 模块概览 -->
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
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import type { DashboardStatsVO } from '@/api/dashboard'

const stats = ref<DashboardStatsVO>({
  totalModules: 0,
  totalExecutors: 0,
  healthyExecutors: 0,
  errorExecutors: 0,
  offlineExecutors: 0,
})

async function fetchStats() {
  try {
    stats.value = await dashboardApi.stats()
  } catch {}
}

onMounted(fetchStats)
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
