<template>
  <div class="settings-ai-learning" v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="$t('settings.ai.learning.hintTitle')"
      class="learning-hint"
    >
      <template #default>
        <p>{{ $t('settings.ai.learning.hintBody') }}</p>
      </template>
    </el-alert>

    <div class="learning-toolbar">
      <el-input
        v-model="filterAppCode"
        clearable
        :placeholder="$t('settings.ai.learning.filterApp')"
        style="width:200px"
      />
      <el-button @click="loadEvents">{{ $t('design.search') }}</el-button>
    </div>

    <el-table
      :data="events"
      size="small"
      :header-cell-style="{ background: '#f5f7fa', color: '#303133', fontWeight: 600 }"
    >
      <el-table-column prop="createdAt" :label="$t('settings.ai.learning.createdAt')" width="160" show-overflow-tooltip />
      <el-table-column prop="appCode" :label="$t('settings.appCode')" width="120" show-overflow-tooltip />
      <el-table-column prop="feature" :label="$t('settings.ai.learning.feature')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="intent" :label="$t('settings.ai.learning.intent')" width="120" show-overflow-tooltip />
      <el-table-column :label="$t('settings.ai.learning.score')" width="90">
        <template #default="{ row }">
          {{ formatScore(row.promotionScore) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('settings.ai.learning.eligible')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.promotionEligible ? 'success' : 'info'" size="small">
            {{ row.promotionEligible ? $t('settings.yes') : $t('settings.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('settings.ai.learning.promoted')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.promotedToRag ? 'success' : 'info'" size="small">
            {{ row.promotedToRag ? $t('settings.yes') : $t('settings.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            class="action-btn"
            type="primary"
            link
            :disabled="!row.promotionEligible || row.promotedToRag"
            @click="handlePromote(row.id)"
          >
            {{ $t('settings.ai.learning.promote') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!events.length && !loading" :description="$t('settings.ai.learning.empty')" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { aiApi, type AiLearningEvent } from '@/api/ai'

const { t } = useI18n()
const loading = ref(false)
const filterAppCode = ref('')
const events = ref<AiLearningEvent[]>([])

function formatScore(score?: number) {
  if (score == null) return '-'
  return `${Math.round(Number(score) * 1000) / 10}%`
}

async function loadEvents() {
  loading.value = true
  try {
    events.value = await aiApi.listLearningEvents(filterAppCode.value.trim() || undefined, 50)
  } catch {
    events.value = []
  } finally {
    loading.value = false
  }
}

async function handlePromote(id: number) {
  try {
    await aiApi.promoteLearningEvent(id)
    ElMessage.success(t('settings.ai.learning.promoteSuccess'))
    await loadEvents()
  } catch (e: any) {
    ElMessage.error(e?.message || t('settings.ai.learning.promoteFailed'))
  }
}

onMounted(loadEvents)
</script>

<style scoped>
.learning-hint {
  margin-bottom: 16px;
}

.learning-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
</style>
