<template>
  <div>
    <div class="page-header">
      <div class="page-stats-row">
        <span style="font-weight:600;color:#409eff">{{ $t('playground.records.total') }} {{ total }}</span>
      </div>
      <el-form inline size="default" class="responsive-filter-form" style="margin-top:12px">
        <el-form-item>
          <el-select v-model="currentAppCode" filterable class="page-filter-control" placeholder="选择应用" @change="handleAppChange">
            <el-option v-for="m in apps" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="filter.keyword" :placeholder="$t('playground.records.sceneNamePlaceholder')" clearable class="page-filter-control" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable class="page-filter-control--sm">
            <el-option
              v-for="item in executionResultOptions"
              :key="item.value"
              :label="item.label"
              :value="item.bindValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="timeShortcuts"
            range-separator="~"
            :start-placeholder="$t('playground.records.startTime')"
            :end-placeholder="$t('playground.records.endTime')"
            class="page-filter-control page-filter-control--wide"
          />
        </el-form-item>
        <el-form-item class="filter-actions-item">
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </div>

    <ResponsiveTable
      :data="recordList"
      :columns="recordColumns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="100"
      @row-click="openDetailDrawer"
    >
      <template #sceneCode="{ row }">
        <span v-if="row.sceneCode" class="code-link" @click.stop="goToSceneDetail(row.sceneCode)">{{ row.sceneCode }}</span>
        <span v-else>-</span>
      </template>
      <template #chainCode="{ row }">
        <span v-if="row.chainCode" class="code-link" @click.stop="openChainDetail(row.chainCode)">{{ row.chainCode }}</span>
        <span v-else>-</span>
      </template>
      <template #instanceId="{ row }">
        <span v-if="row.instanceId" class="code-link" @click.stop="goToLogDetail(row.instanceId)">{{ row.instanceId }}</span>
        <span v-else>-</span>
      </template>
      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
          {{ row.status === 1 ? $t('common.success') : $t('common.failed') }}
        </el-tag>
      </template>
      <template #actions="{ row }">
        <el-button type="primary" link size="small" class="action-btn" @click.stop="openDetailDrawer(row)">{{ $t('common.detail') }}</el-button>
      </template>
    </ResponsiveTable>

    <div class="page-pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :layout="paginationLayout"
        background
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>


    <!-- 详情 Drawer -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('playground.records.detail')"
      :size="detailDrawerSize"
      class="detail-drawer"
    >
      <div v-if="detailData" class="detail-drawer-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('playground.records.sceneCode')">
            <span
              v-if="detailData.sceneCode"
              class="code-link"
              @click="goToSceneDetail(detailData.sceneCode)"
            >{{ detailData.sceneCode }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.sceneName')">
            {{ detailData.sceneName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.executionChainCode')">
            <span
              v-if="detailData.chainCode"
              class="code-link"
              @click="openChainDetail(detailData.chainCode)"
            >{{ detailData.chainCode }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.logCode')">
            <span
              v-if="detailData.instanceId"
              class="code-link"
              @click="goToLogDetail(detailData.instanceId)"
            >{{ detailData.instanceId }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.requestMethod')">
            {{ detailData.requestMethod || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.requestPath')">
            {{ detailData.requestPath || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.status')">
            <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
              {{ detailData.status === 1 ? $t('common.success') : $t('common.failed') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.costMs')">
            {{ detailData.costMs != null ? detailData.costMs + 'ms' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.errorMsg')">
            {{ detailData.errorMsg || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.requestBody')">
            <pre class="detail-json">{{ formatJson(detailData.requestBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.responseBody')">
            <pre class="detail-json">{{ formatJson(detailData.responseBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.requestHeaders')">
            <pre class="detail-json">{{ formatJson(detailData.requestHeaders) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.createdBy')">
            {{ detailData.createdBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.createdAt')">
            {{ detailData.createdAt || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.updatedBy')">
            {{ detailData.updatedBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('playground.records.updatedAt')">
            {{ detailData.updatedAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <ChainDetailDrawer ref="chainDetailDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ChainDetailDrawer from '@/components/ChainDetailDrawer.vue'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { executorApi, type AppOption } from '@/api/executor'
import { queryRecordPage, getRecordById, type PlaygroundRecordVO, type PlaygroundRecordQueryDTO } from '@/api/playground-record'
import { useCurrentApp } from '@/composables/useCurrentApp'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { useResponsivePagination } from '@/composables/useResponsivePagination'
import { useDictLabel } from '@/composables/useDictLabel'

const { t } = useI18n()
const { dictOptions: executionResultOptions } = useDictLabel('execution_result')
const router = useRouter()
const { currentAppCode, syncFromApps } = useCurrentApp()
const { drawerSize: detailDrawerSize } = useResponsiveDrawerSize(600)
const { paginationLayout } = useResponsivePagination()

const recordColumns = computed(() => [
  { prop: 'sceneCode', label: t('playground.records.sceneCode'), width: 180, showOverflowTooltip: true },
  { prop: 'sceneName', label: t('playground.records.sceneName'), minWidth: 140, showOverflowTooltip: true },
  { prop: 'chainCode', label: t('playground.records.executionChainCode'), width: 180, showOverflowTooltip: true },
  { prop: 'instanceId', label: t('playground.records.logCode'), width: 200, showOverflowTooltip: true },
  { prop: 'status', label: t('playground.records.status'), width: 100 },
  { prop: 'costMs', label: t('playground.records.costMs'), width: 100 },
  { prop: 'createdAt', label: t('playground.records.createdAt'), width: 180, showOverflowTooltip: true },
])

const chainDetailDrawerRef = ref<InstanceType<typeof ChainDetailDrawer> | null>(null)

const loading = ref(false)
const recordList = ref<PlaygroundRecordVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const timeRange = ref<string[] | null>(null)
const apps = ref<AppOption[]>([])

const filter = reactive<PlaygroundRecordQueryDTO>({
  keyword: '',
  status: undefined,
})

const timeShortcuts = [
  { text: t('playground.records.last15min'), value: () => [new Date(Date.now() - 15 * 60 * 1000), new Date()] },
  { text: t('playground.records.last1hour'), value: () => [new Date(Date.now() - 60 * 60 * 1000), new Date()] },
  { text: t('playground.records.last24hour'), value: () => [new Date(Date.now() - 24 * 60 * 60 * 1000), new Date()] },
]

// Detail drawer
const detailVisible = ref(false)
const detailData = ref<PlaygroundRecordVO | null>(null)

function goToSceneDetail(sceneCode: string) {
  if (!sceneCode) return
  router.push({
    name: 'PlaygroundScenes',
    query: {
      sceneCode,
      ...(currentAppCode.value ? { appCode: currentAppCode.value } : {}),
    },
  })
}

function goToLogDetail(executionId: string) {
  if (!executionId) return
  router.push({ name: 'Logs', query: { executionId } })
}

function openChainDetail(chainCode: string) {
  if (!chainCode || !currentAppCode.value) return
  chainDetailDrawerRef.value?.open(chainCode, currentAppCode.value)
}

async function loadApps() {
  try {
    const res: any = await executorApi.listApps()
    const data = res.data || res
    apps.value = Array.isArray(data) ? data : []
    syncFromApps(apps.value)
  } catch { /* ignore */ }
}

async function loadData() {
  loading.value = true
  try {
    const params: PlaygroundRecordQueryDTO = {
      ...filter,
      appCode: currentAppCode.value || undefined,
      page: page.value,
      size: size.value,
    }
    if (timeRange.value && timeRange.value.length === 2) {
      params.startTime = timeRange.value[0]
      params.endTime = timeRange.value[1]
    }
    const res: any = await queryRecordPage(params)
    const data = res.data || res
    recordList.value = data.records || []
    total.value = data.total || 0
  } catch {
    recordList.value = []
  } finally {
    loading.value = false
  }
}

function handleAppChange() { page.value = 1; loadData() }
function handleSearch() {
  page.value = 1
  loadData()
}

function handleReset() {
  filter.keyword = ''
  filter.status = undefined
  timeRange.value = null
  page.value = 1
  loadData()
}

async function openDetailDrawer(row: PlaygroundRecordVO) {
  detailVisible.value = true
  detailData.value = null
  try {
    const res: any = await getRecordById(row.id)
    detailData.value = res.data || res
  } catch {
    detailData.value = row
  }
}

function formatJson(str: string | null | undefined): string {
  if (!str) return '-'
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}

onMounted(() => {
  loadApps()
  loadData()
})
</script>

<style scoped>
.detail-json { margin: 0; font-family: monospace; font-size: 12px; white-space: pre-wrap; max-height: 200px; overflow-y: auto; background: #f5f7fa; padding: 8px; border-radius: 4px; }
.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
