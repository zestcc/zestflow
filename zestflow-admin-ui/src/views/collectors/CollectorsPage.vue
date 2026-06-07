<template>
  <div class="collectors-page">
    <div class="page-header">
      <div class="stats-summary">
        <span class="summary-total">{{ $t('settings.collectorTotal') }} {{ list.length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-healthy">{{ $t('settings.collectorHealthy') }} {{ list.filter(c => c.status === 1).length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-error">{{ $t('settings.collectorError') }} {{ list.filter(c => c.status === 2).length }}</span>
        <el-divider direction="vertical" />
        <span class="summary-offline">{{ $t('settings.collectorOffline') }} {{ list.filter(c => c.status === 0).length }}</span>
      </div>
    </div>

    <el-form :model="filter" inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
      <el-form-item label="应用编码">
        <el-input v-model="filter.appCode" placeholder="输入应用编码" clearable class="page-filter-control--xs" />
      </el-form-item>
      <el-form-item :label="$t('collectors.collectorId')">
        <el-input v-model="filter.collectorId" :placeholder="$t('collectors.collectorIdPlaceholder')" clearable class="page-filter-control--md" />
      </el-form-item>
      <el-form-item :label="$t('collectors.address')">
        <el-input v-model="filter.address" placeholder="输入地址" clearable class="page-filter-control--md" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable class="page-filter-control--sm">
          <el-option
            v-for="item in registryStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.bindValue"
          />
        </el-select>
      </el-form-item>
      <el-form-item class="filter-actions-item">
        <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <ResponsiveTable
      :data="paginatedList"
      :columns="collectorColumns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="170"
    >
      <template #collectorId="{ row }">{{ row.collectorId }}</template>
      <template #appCode="{ row }">{{ row.appCode || '-' }}</template>
      <template #appName="{ row }">{{ row.appName || '-' }}</template>
      <template #address="{ row }">
        <span style="font-family:monospace;font-size:13px">{{ row.collectorHost }}:{{ row.collectorPort }}</span>
      </template>
      <template #status="{ row }">
        <el-tag :type="registryStatusTagType(row.status)" size="small" effect="dark">{{ registryStatusLabel(row.status) }}</el-tag>
      </template>
      <template #lastHeartbeat="{ row }">{{ formatTime(row.lastHeartbeat) }}</template>
      <template #updatedBy="{ row }">{{ row.updatedBy || '-' }}</template>
      <template #actions="{ row }">
        <el-button
          v-if="row.status !== 1"
          text type="success" size="small" class="action-btn"
          :loading="togglingId === row.id"
          @click.stop="toggleStatus(row, 1)"
        >{{ $t('collectors.onlineBtn') }}</el-button>
        <el-button
          v-if="row.status !== 0"
          text type="warning" size="small" class="action-btn"
          :loading="togglingId === row.id"
          @click.stop="toggleStatus(row, 0)"
        >{{ $t('collectors.offlineBtn') }}</el-button>
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
import { collectorApi } from '@/api/collector'
import type { CollectorRegistryVO } from '@/api/collector'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsivePagination } from '@/composables/useResponsivePagination'
import { useDictLabel } from '@/composables/useDictLabel'

const { t } = useI18n()
const { dictOptions: registryStatusOptions, labelOf: registryStatusLabel, tagTypeOf: registryStatusTagType } = useDictLabel('registry_status')
const { paginationLayout } = useResponsivePagination()

const loading = ref(false)
const list = ref<CollectorRegistryVO[]>([])

const filter = ref({
  appCode: '',
  collectorId: '',
  address: '',
  status: '' as number | string,
})

const page = ref(1)
const pageSize = ref(10)

const collectorColumns = computed(() => [
  { prop: 'collectorId', label: t('collectors.collectorId'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'appCode', label: '应用编码', width: 120, showOverflowTooltip: true },
  { prop: 'appName', label: '应用名称', width: 140, showOverflowTooltip: true },
  { prop: 'address', label: t('collectors.address'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 90, align: 'center' as const },
  { prop: 'lastHeartbeat', label: t('collectors.lastHeartbeat'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'updatedBy', label: t('common.updatedBy'), width: 120, showOverflowTooltip: true },
])

const filteredList = computed(() => {
  let arr = list.value
  const f = filter.value
  if (f.appCode) arr = arr.filter(c => c.appCode?.includes(f.appCode))
  if (f.collectorId) arr = arr.filter(c => c.collectorId.includes(f.collectorId))
  if (f.address) arr = arr.filter(c => `${c.collectorHost}:${c.collectorPort}`.includes(f.address))
  if (f.status === 0 || f.status === 1 || f.status === 2) arr = arr.filter(c => c.status === f.status)
  return arr
})

const paginatedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

function handleSearch() { page.value = 1 }
function handleReset() {
  filter.value = { appCode: '', collectorId: '', address: '', status: '' }
  page.value = 1
}

function formatTime(ts: string | null): string {
  if (!ts) return '-'
  return ts.replace('T', ' ')
}

const togglingId = ref<number | null>(null)

async function toggleStatus(row: CollectorRegistryVO, newStatus: number) {
  togglingId.value = row.id
  try {
    await collectorApi.updateStatus(row.id, newStatus)
    ElMessage.success(t(newStatus === 1 ? 'collectors.onlineSuccess' : 'collectors.offlineSuccess'))
    const idx = list.value.findIndex(c => c.id === row.id)
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
    list.value = await collectorApi.list()
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
