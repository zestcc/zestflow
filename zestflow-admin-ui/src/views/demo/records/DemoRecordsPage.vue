<template>
  <div>
    <div class="page-header">
      <h2>{{ $t('demo.records.title') }}</h2>
    </div>

    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="filter" inline size="small" @keyup.enter="handleSearch">
        <el-form-item :label="$t('demo.records.sceneName')">
          <el-input v-model="filter.keyword" :placeholder="$t('demo.records.sceneNamePlaceholder')" clearable />
        </el-form-item>
        <el-form-item :label="$t('demo.records.status')">
          <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable style="width:120px">
            <el-option :label="$t('common.success')" :value="1" />
            <el-option :label="$t('common.failed')" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('demo.records.timeRange')">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="timeShortcuts"
            range-separator="~"
            :start-placeholder="$t('demo.records.startTime')"
            :end-placeholder="$t('demo.records.endTime')"
            style="width:340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table
        :data="recordList"
        v-loading="loading"
        :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
        @row-click="openDetailDrawer"
      >
        <el-table-column prop="sceneCode" :label="$t('demo.records.sceneCode')" width="180" show-overflow-tooltip />
        <el-table-column prop="sceneName" :label="$t('demo.records.sceneName')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="chainCode" :label="$t('demo.records.chainCode')" width="180" show-overflow-tooltip />
        <el-table-column :label="$t('demo.records.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? $t('common.success') : $t('common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" :label="$t('demo.records.costMs')" width="100" />
        <el-table-column prop="createdAt" :label="$t('demo.records.createdAt')" width="180" show-overflow-tooltip />
        <el-table-column :label="$t('common.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" class="action-btn" @click.stop="openDetailDrawer(row)">
              {{ $t('common.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情 Drawer -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('demo.records.detail')"
      size="600px"
    >
      <template v-if="detailData">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('demo.records.sceneCode')">
            {{ detailData.sceneCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.sceneName')">
            {{ detailData.sceneName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.chainCode')">
            {{ detailData.chainCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.instanceId')">
            {{ detailData.instanceId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.requestMethod')">
            {{ detailData.requestMethod || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.requestPath')">
            {{ detailData.requestPath || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.status')">
            <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
              {{ detailData.status === 1 ? $t('common.success') : $t('common.failed') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.costMs')">
            {{ detailData.costMs != null ? detailData.costMs + 'ms' : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.errorMsg')">
            {{ detailData.errorMsg || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.requestBody')">
            <pre class="detail-json">{{ formatJson(detailData.requestBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.responseBody')">
            <pre class="detail-json">{{ formatJson(detailData.responseBody) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.requestHeaders')">
            <pre class="detail-json">{{ formatJson(detailData.requestHeaders) }}</pre>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.createdBy')">
            {{ detailData.createdBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.createdAt')">
            {{ detailData.createdAt || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.updatedBy')">
            {{ detailData.updatedBy || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('demo.records.updatedAt')">
            {{ detailData.updatedAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { queryRecordPage, getRecordById, type DemoRecordVO, type DemoRecordQueryDTO } from '@/api/demo-record'

const { t } = useI18n()

const loading = ref(false)
const recordList = ref<DemoRecordVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const timeRange = ref<string[] | null>(null)

const filter = reactive<DemoRecordQueryDTO>({
  keyword: '',
  status: undefined,
})

const timeShortcuts = [
  { text: t('demo.records.last15min'), value: () => [new Date(Date.now() - 15 * 60 * 1000), new Date()] },
  { text: t('demo.records.last1hour'), value: () => [new Date(Date.now() - 60 * 60 * 1000), new Date()] },
  { text: t('demo.records.last24hour'), value: () => [new Date(Date.now() - 24 * 60 * 60 * 1000), new Date()] },
]

// Detail drawer
const detailVisible = ref(false)
const detailData = ref<DemoRecordVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const params: DemoRecordQueryDTO = {
      ...filter,
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

async function openDetailDrawer(row: DemoRecordVO) {
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
  loadData()
})
</script>

<style scoped>
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.filter-card { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.detail-json { margin: 0; font-family: monospace; font-size: 12px; white-space: pre-wrap; max-height: 200px; overflow-y: auto; background: #f5f7fa; padding: 8px; border-radius: 4px; }
.action-btn { padding: 2px 4px; margin-left: 0; }
</style>
