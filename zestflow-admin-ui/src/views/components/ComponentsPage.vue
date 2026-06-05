<template>
  <div class="components-page">
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('components.total') }} {{ statsObj.total }}</span>
        <el-tag type="success" size="small" style="margin-left:8px">{{ $t('components.active') }} {{ statsObj.active }}</el-tag>
        <el-tag type="info" size="small">{{ $t('components.offline') }} {{ statsObj.offline }}</el-tag>
        <el-select
          v-model="currentAppCode"
          filterable
          style="width:200px;margin-left:16px"
          :placeholder="$t('components.selectApp')"
          @change="handleModuleChange"
        >
          <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
        </el-select>
      </div>
    </div>

    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item :label="$t('components.keyword')">
        <el-input v-model="filter.keyword" :placeholder="$t('components.keyword')" clearable style="width:200px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('components.status')">
        <el-select v-model="filter.status" :placeholder="$t('components.total')" clearable style="width:100px">
          <el-option :label="$t('components.active')" :value="1" />
          <el-option :label="$t('components.offline')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('components.componentType')">
        <el-select v-model="filter.componentType" clearable style="width:130px" :placeholder="$t('common.all')">
          <el-option v-for="item in componentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ $t('components.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('components.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="componentList"
      v-loading="loading"
      stripe border
      style="width:100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="componentId" :label="$t('components.componentId')" width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="code-link" @click="showDetail(row)">{{ row.componentId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="componentName" :label="$t('components.componentName')" show-overflow-tooltip min-width="80" />
      <el-table-column :label="$t('components.componentType')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.componentType)" size="small">
            {{ typeLabel(row.componentType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="groupName" :label="$t('components.groupName')" width="120" show-overflow-tooltip />
      <el-table-column prop="executorSource" :label="$t('components.executorSource')" width="160" show-overflow-tooltip />
      <el-table-column :label="$t('components.timeout')" width="90" align="center">
        <template #default="{ row }">{{ row.timeout === -1 ? '-' : row.timeout }}</template>
      </el-table-column>
      <el-table-column :label="$t('components.isAsync')" width="60" align="center">
        <template #default="{ row }">
          <el-tag :type="row.async ? 'warning' : 'info'" size="small">
            {{ row.async ? $t('components.yes') : $t('components.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('components.status')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('components.active') : $t('components.offline') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cachedAt" :label="$t('components.updatedAt')" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.cachedAt }}</template>
      </el-table-column>
    </el-table>

    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="page=1;fetchList()"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="$t('components.detail')" size="600px" destroy-on-close>
      <template v-if="selectedComp">
        <el-descriptions :column="1" border style="margin-bottom:16px">
          <el-descriptions-item :label="$t('components.componentId')">
            <span style="font-family:monospace">{{ selectedComp.componentId }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.componentName')">
            {{ selectedComp.componentName }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.componentType')">
            <el-tag :type="typeTagType(selectedComp.componentType)" size="small">
              {{ typeLabel(selectedComp.componentType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.description')" v-if="selectedComp.description">
            {{ selectedComp.description }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.groupName')">
            {{ selectedComp.groupName }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.executorSource')" v-if="selectedComp.executorSource">
            {{ selectedComp.executorSource }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.timeout')">
            {{ selectedComp.timeout === -1 ? '-' : selectedComp.timeout }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.isAsync')">
            <el-tag :type="selectedComp.async ? 'warning' : 'info'" size="small">
              {{ selectedComp.async ? $t('components.yes') : $t('components.no') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.status')">
            <el-tag :type="selectedComp.status === 1 ? 'success' : 'info'" size="small">
              {{ selectedComp.status === 1 ? $t('components.active') : $t('components.offline') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.updatedAt')" v-if="selectedComp.cachedAt">
            {{ selectedComp.cachedAt }}
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border v-if="selectedComp.tagDefs && selectedComp.tagDefs.length > 0" style="margin-bottom:16px">
          <el-descriptions-item :label="$t('components.tags')">
            <el-table :data="selectedComp.tagDefs" border size="small" style="width:100%">
              <el-table-column :label="$t('components.tagName')" prop="name" show-overflow-tooltip />
              <el-table-column :label="$t('components.tagValue')" prop="value" show-overflow-tooltip width="160">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.value }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { componentApi } from '@/api/component'
import { useDict } from '@/composables/useDict'

const { t } = useI18n()

const { options: componentTypeOptions } = useDict('component_type')

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    EXECUTOR: t('components.typeExecutor'),
    PREDICATE: t('components.typePredicate'),
    SELECTOR: t('components.typeSelector'),
    LOADER: t('components.typeLoader'),
    PARSER: t('components.typeParser'),
    PRE_PROCESSOR: t('components.typePreProcessor'),
    POST_PROCESSOR: t('components.typePostProcessor'),
    PARAM_BINDER: t('components.typeParamBinder'),
    PARAM_VALIDATOR: t('components.typeParamValidator'),
  }
  return map[type] || type
}

function typeTagType(type: string): string {
  const map: Record<string, string> = {
    EXECUTOR: 'primary',
    PREDICATE: 'warning',
    SELECTOR: '',
    LOADER: 'cyan',
    PARSER: 'success',
    PRE_PROCESSOR: '',
    POST_PROCESSOR: 'success',
    PARAM_BINDER: '',
    PARAM_VALIDATOR: 'success',
  }
  return map[type] || 'info'
}
import type { ComponentVO } from '@/api/component'
import { executorApi, type AppOption } from '@/api/executor'

const loading = ref(false)
const componentList = ref<ComponentVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const modules = ref<AppOption[]>([])
const currentAppCode = ref<string>('')

const drawerVisible = ref(false)
const selectedComp = ref<ComponentVO | null>(null)

function showDetail(row: ComponentVO) {
  selectedComp.value = row
  drawerVisible.value = true
}

const filter = ref({
  keyword: '',
  status: '' as number | string,
  componentType: '' as string,
})

const statsObj = ref({ total: 0, active: 0, offline: 0 })

async function fetchStats() {
  if (!currentAppCode.value) return
  try {
    const res = await componentApi.stats({ appCode: currentAppCode.value })
    statsObj.value = res
  } catch {
    // stats 不影响列表展示
  }
}

async function fetchList() {
  if (!currentAppCode.value) { componentList.value = []; total.value = 0; return }
  loading.value = true
  try {
    const res = await componentApi.list({
      appCode: currentAppCode.value,
      keyword: filter.value.keyword || undefined,
      status: filter.value.status === '' ? undefined : (filter.value.status as number),
      componentType: filter.value.componentType || undefined,
      page: page.value,
      size: pageSize.value,
    })
    componentList.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchList()
  fetchStats()
}

function handleReset() {
  filter.value = { keyword: '', status: '', componentType: '' }
  page.value = 1
  fetchList()
  fetchStats()
}

async function handleModuleChange() {
  page.value = 1
  await fetchList()
  await fetchStats()
}

onMounted(async () => {
  modules.value = await executorApi.listApps()
  if (modules.value.length > 0) {
    currentAppCode.value = modules.value[0].appCode
    await handleModuleChange()
  }
  await fetchStats()
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.stats-summary {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
</style>
