<template>
  <div class="settings-ai-usage" v-loading="loading">
    <div class="usage-toolbar">
      <span>{{ $t('settings.ai.usage.window') }}</span>
      <el-select v-model="days" style="width:120px" @change="loadOverview">
        <el-option :label="$t('settings.ai.usage.days7')" :value="7" />
        <el-option :label="$t('settings.ai.usage.days30')" :value="30" />
        <el-option :label="$t('settings.ai.usage.days90')" :value="90" />
      </el-select>
      <el-button @click="loadOverview">{{ $t('design.search') }}</el-button>
    </div>

    <el-row :gutter="12" class="usage-cards">
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never"><div class="metric-value">{{ overview?.totalSessions ?? 0 }}</div><div class="metric-label">{{ $t('settings.ai.usage.totalSessions') }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never"><div class="metric-value">{{ overview?.successRate ?? 0 }}%</div><div class="metric-label">{{ $t('settings.ai.usage.successRate') }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never"><div class="metric-value">{{ overview?.avgLatencyMs ?? 0 }}ms</div><div class="metric-label">{{ $t('settings.ai.usage.avgLatency') }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never"><div class="metric-value">{{ overview?.totalTokenEstimate ?? 0 }}</div><div class="metric-label">{{ $t('settings.ai.usage.tokenEstimate') }}</div></el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never"><div class="metric-value">{{ overview?.adoptedRate ?? 0 }}%</div><div class="metric-label">{{ $t('settings.ai.usage.adoptedRate') }}</div></el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="usage-section">
      <template #header>{{ $t('settings.ai.usage.byMode') }}</template>
      <el-table :data="modeRows" size="small">
        <el-table-column prop="mode" :label="$t('settings.ai.usage.mode')" />
        <el-table-column prop="count" :label="$t('settings.ai.usage.sessions')" width="120" />
      </el-table>
      <el-empty v-if="modeRows.length === 0" :description="$t('settings.ai.usage.empty')" />
    </el-card>

    <el-card shadow="never" class="usage-section">
      <template #header>{{ $t('settings.ai.usage.dailyTrend') }}</template>
      <el-table :data="overview?.dailyTrend ?? []" size="small" max-height="320">
        <el-table-column prop="date" :label="$t('settings.ai.usage.date')" width="120" />
        <el-table-column prop="sessions" :label="$t('settings.ai.usage.sessions')" width="100" />
        <el-table-column prop="successSessions" :label="$t('settings.ai.usage.successSessions')" width="120" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { aiApi, type AiUsageOverview } from '@/api/ai'

const loading = ref(false)
const days = ref(30)
const overview = ref<AiUsageOverview | null>(null)

const modeRows = computed(() => {
  const map = overview.value?.sessionsByMode ?? {}
  return Object.entries(map).map(([mode, count]) => ({ mode, count }))
})

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await aiApi.getUsageOverview(days.value)
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.usage-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.usage-cards {
  margin-bottom: 16px;
}
.metric-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.metric-label {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.usage-section {
  margin-bottom: 16px;
}
</style>
