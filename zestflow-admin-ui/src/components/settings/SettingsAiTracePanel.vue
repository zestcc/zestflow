<template>
  <div class="settings-ai-trace" v-loading="loading">
    <div class="trace-toolbar">
      <span>{{ $t('settings.ai.trace.window') }}</span>
      <el-select v-model="days" style="width:120px" @change="loadOverview">
        <el-option :label="$t('settings.ai.usage.days7')" :value="7" />
        <el-option :label="$t('settings.ai.usage.days30')" :value="30" />
        <el-option :label="$t('settings.ai.usage.days90')" :value="90" />
      </el-select>
      <el-button @click="loadOverview">{{ $t('design.search') }}</el-button>
    </div>

    <el-row :gutter="12" class="trace-cards">
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never">
          <div class="metric-value">{{ overview?.totalSteps ?? 0 }}</div>
          <div class="metric-label">{{ $t('settings.ai.trace.totalSteps') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never">
          <div class="metric-value">{{ overview?.failedSteps ?? 0 }}</div>
          <div class="metric-label">{{ $t('settings.ai.trace.failedSteps') }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never">
          <div class="metric-value">{{ overview?.avgStepLatencyMs ?? 0 }}ms</div>
          <div class="metric-label">{{ $t('settings.ai.trace.avgStepLatency') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="trace-section">
      <template #header>{{ $t('settings.ai.trace.byType') }}</template>
      <el-table :data="typeRows" size="small">
        <el-table-column prop="type" :label="$t('settings.ai.trace.stepType')" />
        <el-table-column prop="count" :label="$t('settings.ai.usage.sessions')" width="120" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="trace-section">
      <template #header>{{ $t('settings.ai.trace.recentSessions') }}</template>
      <el-table
        :data="overview?.recentSessions ?? []"
        size="small"
        :header-cell-style="{ background: '#f5f7fa', color: '#303133', fontWeight: 600 }"
        @row-click="openTraceDrawer"
      >
        <el-table-column prop="sessionId" :label="$t('settings.ai.trace.sessionId')" width="100" />
        <el-table-column prop="title" :label="$t('settings.ai.trace.sessionTitle')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="mode" :label="$t('settings.ai.usage.mode')" width="100" show-overflow-tooltip />
        <el-table-column prop="stepCount" :label="$t('settings.ai.trace.stepCount')" width="90" />
        <el-table-column prop="totalLatencyMs" :label="$t('settings.ai.trace.totalLatency')" width="110" />
        <el-table-column prop="createdAt" :label="$t('settings.ai.learning.createdAt')" width="160" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-drawer v-model="traceDrawerVisible" :title="$t('settings.ai.trace.detailTitle')" size="480px" append-to-body>
      <el-table
        v-loading="traceLoading"
        :data="traceSteps"
        size="small"
        :header-cell-style="{ background: '#f5f7fa', color: '#303133', fontWeight: 600 }"
      >
        <el-table-column prop="stepType" :label="$t('settings.ai.trace.stepType')" width="90" />
        <el-table-column prop="stepName" :label="$t('settings.ai.trace.stepName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" :label="$t('common.status')" width="80" />
        <el-table-column prop="latencyMs" :label="$t('settings.ai.trace.stepLatency')" width="90" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { aiApi, type AiCopilotTraceOverview, type AiCopilotTraceStep } from '@/api/ai'

const loading = ref(false)
const days = ref(30)
const overview = ref<AiCopilotTraceOverview | null>(null)
const traceDrawerVisible = ref(false)
const traceLoading = ref(false)
const traceSteps = ref<AiCopilotTraceStep[]>([])

const typeRows = computed(() => {
  const map = overview.value?.stepsByType ?? {}
  return Object.entries(map).map(([type, count]) => ({ type, count }))
})

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await aiApi.getTraceOverview(days.value)
  } catch {
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function openTraceDrawer(row: { sessionId: number }) {
  traceDrawerVisible.value = true
  traceLoading.value = true
  try {
    traceSteps.value = await aiApi.getSessionTrace(row.sessionId)
  } catch {
    traceSteps.value = []
  } finally {
    traceLoading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.trace-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.trace-cards {
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

.trace-section {
  margin-bottom: 16px;
}
</style>
