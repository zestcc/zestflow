<template>
  <div class="components-page">
    <div class="page-header">
      <div class="page-header-row">
        <div class="page-stats-row">
          <span style="font-weight:600;color:#409eff">{{ $t('components.total') }} {{ statsObj.total }}</span>
          <el-tag type="success" size="small">{{ $t('components.active') }} {{ statsObj.active }}</el-tag>
          <el-tag type="info" size="small">{{ $t('components.offline') }} {{ statsObj.offline }}</el-tag>
          <el-select
            v-model="currentAppCode"
            filterable
            class="page-filter-control"
            :placeholder="$t('components.selectApp')"
            @change="handleModuleChange"
          >
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
          </el-select>
          <el-button type="primary" plain @click="openScaffold">{{ $t('ai.scaffold.menu') }}</el-button>
        </div>
      </div>
    </div>

    <el-form :model="filter" inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
      <el-form-item :label="$t('components.keyword')">
        <el-input v-model="filter.keyword" :placeholder="$t('components.keyword')" clearable class="page-filter-control" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item :label="$t('components.status')">
        <el-select v-model="filter.status" :placeholder="$t('components.total')" clearable class="page-filter-control--sm">
          <el-option
            v-for="item in componentOnlineFilterOptions"
            :key="item.value"
            :label="item.label"
            :value="item.bindValue"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('components.componentType')">
        <el-select v-model="filter.componentType" clearable class="page-filter-control--sm" :placeholder="$t('common.all')">
          <el-option v-for="item in componentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item class="filter-actions-item">
        <el-button type="primary" @click="handleSearch">{{ $t('components.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('components.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <ResponsiveTable
      :data="componentList"
      :columns="componentColumns"
      :loading="loading"
      row-key="componentId"
    >
      <template #componentId="{ row }">
        <span class="code-link" @click="showDetail(row)">{{ row.componentId }}</span>
      </template>
      <template #componentType="{ row }">
        <el-tag size="small" :type="componentTypeTagType(row.componentType)">
          {{ componentTypeLabel(row.componentType) }}
        </el-tag>
      </template>
      <template #timeout="{ row }">{{ row.timeout === -1 ? '-' : row.timeout }}</template>
      <template #async="{ row }">
        <el-tag :type="row.async ? 'warning' : 'info'" size="small">
          {{ row.async ? $t('components.yes') : $t('components.no') }}
        </el-tag>
      </template>
      <template #status="{ row }">
        <el-tag :type="registryStatusTagType(row.status)" size="small">
          {{ registryStatusLabel(row.status) }}
        </el-tag>
      </template>
      <template #cachedAt="{ row }">{{ row.cachedAt }}</template>
    </ResponsiveTable>

    <div class="page-pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="paginationLayout"
        @current-change="fetchList"
        @size-change="page=1;fetchList()"
      />
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="$t('components.detail')"
      :size="drawerSize"
      class="detail-drawer"
      destroy-on-close
    >
      <div v-if="selectedComp" class="detail-drawer-body">
        <el-descriptions :column="1" border style="margin-bottom:16px">
          <el-descriptions-item :label="$t('components.componentId')">
            <span style="font-family:monospace">{{ selectedComp.componentId }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.componentName')">
            {{ selectedComp.componentName }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.componentType')">
            <el-tag :type="componentTypeTagType(selectedComp.componentType)" size="small">
              {{ componentTypeLabel(selectedComp.componentType) }}
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
            <el-tag :type="registryStatusTagType(selectedComp.status)" size="small">
              {{ registryStatusLabel(selectedComp.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('components.updatedAt')" v-if="selectedComp.cachedAt">
            {{ selectedComp.cachedAt }}
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border v-if="selectedComp.tagDefs && selectedComp.tagDefs.length > 0" style="margin-bottom:16px">
          <el-descriptions-item :label="$t('components.tags')">
            <ResponsiveTable
              :data="selectedComp.tagDefs"
              :columns="tagColumns"
              :row-key="tagRowKey"
            >
              <template #value="{ row }">
                <el-tag size="small" type="info">{{ row.value }}</el-tag>
              </template>
            </ResponsiveTable>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <AiComponentScaffoldDialog ref="scaffoldDialogRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { componentApi } from '@/api/component'
import type { ComponentVO } from '@/api/component'
import { executorApi, type AppOption } from '@/api/executor'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import AiComponentScaffoldDialog from '@/components/ai/AiComponentScaffoldDialog.vue'
import { useDictLabel } from '@/composables/useDictLabel'
import { useCurrentApp } from '@/composables/useCurrentApp'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const { options: componentTypeOptions, labelOf: componentTypeLabel, tagTypeOf: componentTypeTagType } = useDictLabel('component_type')
const { dictOptions: componentOnlineOptions, labelOf: registryStatusLabel, tagTypeOf: registryStatusTagType } = useDictLabel('registry_status')
const componentOnlineFilterOptions = computed(() =>
  componentOnlineOptions.value.filter(o => o.value === '0' || o.value === '1'),
)
const { currentAppCode, syncFromApps } = useCurrentApp()
const { drawerSize } = useResponsiveDrawerSize(600)
const { paginationLayout } = useResponsivePagination()
const scaffoldDialogRef = ref<InstanceType<typeof AiComponentScaffoldDialog> | null>(null)

const componentColumns = computed(() => [
  { prop: 'componentId', label: t('components.componentId'), width: 240, showOverflowTooltip: true },
  { prop: 'componentName', label: t('components.componentName'), minWidth: 80, showOverflowTooltip: true },
  { prop: 'componentType', label: t('components.componentType'), width: 100, align: 'center' as const },
  { prop: 'groupName', label: t('components.groupName'), width: 120, showOverflowTooltip: true },
  { prop: 'executorSource', label: t('components.executorSource'), width: 160, showOverflowTooltip: true },
  { prop: 'timeout', label: t('components.timeout'), width: 90, align: 'center' as const },
  { prop: 'async', label: t('components.isAsync'), width: 60, align: 'center' as const },
  { prop: 'status', label: t('components.status'), width: 80, align: 'center' as const },
  { prop: 'cachedAt', label: t('components.updatedAt'), width: 160, showOverflowTooltip: true },
])

const tagColumns = computed(() => [
  { prop: 'name', label: t('components.tagName'), showOverflowTooltip: true },
  { prop: 'value', label: t('components.tagValue'), width: 160, showOverflowTooltip: true },
])

function tagRowKey(row: { name: string }) {
  return row.name
}

const loading = ref(false)
const componentList = ref<ComponentVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const modules = ref<AppOption[]>([])

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

function openScaffold() {
  scaffoldDialogRef.value?.open(currentAppCode.value || undefined)
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
  syncFromApps(modules.value)
  if (currentAppCode.value) {
    await handleModuleChange()
  }
  await fetchStats()
})
</script>
