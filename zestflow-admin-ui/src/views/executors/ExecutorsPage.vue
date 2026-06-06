<template>
  <div class="executors-page">
    <div class="page-header">
      <div class="stats-summary">
        <span class="summary-total">{{ $t('settings.executorTotal') }} {{ list.length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-healthy">{{ $t('settings.executorHealthy') }} {{ list.filter(e => e.status === 1).length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-error">{{ $t('settings.executorError') }} {{ list.filter(e => e.status === 2).length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-offline">{{ $t('settings.executorOffline') }} {{ list.filter(e => e.status === 0).length }}</span>
      </div>
    </div>

    <el-form :model="filter" inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
      <el-form-item label="应用编码">
        <el-input v-model="filter.appCode" placeholder="输入应用编码" clearable class="page-filter-control--xs" />
      </el-form-item>
      <el-form-item label="执行器ID">
        <el-input v-model="filter.executorId" placeholder="输入执行器ID" clearable class="page-filter-control--md" />
      </el-form-item>
      <el-form-item label="地址">
        <el-input v-model="filter.address" placeholder="输入地址" clearable class="page-filter-control--md" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable class="page-filter-control--sm">
          <el-option :label="$t('settings.executorHealthy')" :value="1" />
          <el-option :label="$t('settings.executorError')" :value="2" />
          <el-option :label="$t('settings.executorOffline')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item class="filter-actions-item">
        <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <ResponsiveTable
      :data="paginatedList"
      :columns="executorColumns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="170"
    >
      <template #executorId="{ row }">{{ row.executorId }}</template>
      <template #appCode="{ row }">{{ row.appCode || '-' }}</template>
      <template #appName="{ row }">{{ row.appName || '-' }}</template>
      <template #address="{ row }">
        <span style="font-family:monospace;font-size:13px">{{ row.executorHost }}:{{ row.executorPort }}</span>
      </template>
      <template #status="{ row }">
        <el-tag v-if="row.status === 1" type="success" size="small" effect="dark">{{ $t('settings.executorHealthy') }}</el-tag>
        <el-tag v-else-if="row.status === 2" type="danger" size="small" effect="dark">{{ $t('settings.executorError') }}</el-tag>
        <el-tag v-else type="info" size="small" effect="dark">{{ $t('settings.executorOffline') }}</el-tag>
      </template>
      <template #lastHeartbeat="{ row }">{{ formatTime(row.lastHeartbeat) }}</template>
      <template #declaredChainKeys="{ row }">
        <span v-if="!row.declaredChainKeys?.length" style="color:#c0c4cc">{{ $t('settings.declaredChainKeysEmpty') }}</span>
        <span v-else>{{ row.declaredChainKeys.join(', ') }}</span>
      </template>
      <template #updatedBy="{ row }">{{ row.updatedBy || '-' }}</template>
      <template #actions="{ row }">
        <el-button
          v-if="row.status !== 1"
          text type="success" size="small" class="action-btn"
          :loading="togglingId === row.id"
          @click.stop="toggleStatus(row, 1)"
        >{{ $t('settings.online') }}</el-button>
        <el-button
          v-if="row.status !== 0"
          text type="warning" size="small" class="action-btn"
          :loading="togglingId === row.id"
          @click.stop="toggleStatus(row, 0)"
        >{{ $t('settings.offline') }}</el-button>
      </template>
    </ResponsiveTable>

    <div class="page-pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="filteredList.length"
        :page-sizes="[10, 20, 50]"
        :layout="paginationLayout"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { executorApi } from '@/api/executor'
import type { ExecutorRegistryVO } from '@/api/executor'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const { paginationLayout } = useResponsivePagination()

const loading = ref(false)
const list = ref<ExecutorRegistryVO[]>([])

const filter = ref({
  appCode: '',
  executorId: '',
  address: '',
  status: '' as number | string,
})

const page = ref(1)
const pageSize = ref(10)

const executorColumns = computed(() => [
  { prop: 'executorId', label: t('settings.executorId'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'appCode', label: '应用编码', width: 120, showOverflowTooltip: true },
  { prop: 'appName', label: '应用名称', width: 140, showOverflowTooltip: true },
  { prop: 'address', label: t('settings.executorAddress'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 90, align: 'center' as const },
  { prop: 'lastHeartbeat', label: t('settings.lastHeartbeat'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'declaredChainKeys', label: t('settings.declaredChainKeys'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'updatedBy', label: t('common.updatedBy'), width: 120, showOverflowTooltip: true },
])

const filteredList = computed(() => {
  let arr = list.value
  const f = filter.value
  if (f.appCode) arr = arr.filter(e => e.appCode?.includes(f.appCode))
  if (f.executorId) arr = arr.filter(e => e.executorId.includes(f.executorId))
  if (f.address) arr = arr.filter(e => `${e.executorHost}:${e.executorPort}`.includes(f.address))
  if (f.status === 0 || f.status === 1 || f.status === 2) arr = arr.filter(e => e.status === f.status)
  return arr
})

const paginatedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

function handleSearch() { page.value = 1 }
function handleReset() {
  filter.value = { appCode: '', executorId: '', address: '', status: '' }
  page.value = 1
}

function formatTime(ts: string | null): string {
  if (!ts) return '-'
  return ts.replace('T', ' ')
}

const togglingId = ref<number | null>(null)

async function toggleStatus(row: ExecutorRegistryVO, newStatus: number) {
  togglingId.value = row.id
  try {
    await executorApi.updateStatus(row.id, newStatus)
    ElMessage.success(t(newStatus === 1 ? 'settings.onlineSuccess' : 'settings.offlineSuccess'))
    const idx = list.value.findIndex(e => e.id === row.id)
    if (idx !== -1) list.value[idx].status = newStatus
  } catch {
    await fetchList()
  } finally {
    togglingId.value = null
  }
}

async function fetchList() {
  loading.value = true
  try {
    list.value = await executorApi.list()
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.summary-total { font-weight: 600; color: #409eff; }
.summary-healthy { font-weight: 600; color: #67c23a; }
.summary-error { font-weight: 600; color: #f56c6c; }
.summary-offline { font-weight: 600; color: #c0c4cc; }
</style>
