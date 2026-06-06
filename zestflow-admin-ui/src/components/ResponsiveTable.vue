<template>
  <div v-loading="loading" class="responsive-table-root">
    <!-- 移动端：卡片列表 -->
    <div v-if="isMobile" class="responsive-table-cards">
      <el-empty v-if="!data.length" :description="emptyText" />
      <template v-else>
        <div
          v-for="item in data"
          :key="getRowKey(item)"
          class="responsive-card"
          @click="handleCardClick(item)"
        >
          <div
            v-for="column in columns"
            :key="column.prop"
            class="card-row"
          >
            <span class="card-label">{{ column.label }}</span>
            <span class="card-value">
              <slot :name="column.prop" :row="item">
                {{ formatValue(item, column) }}
              </slot>
            </span>
          </div>
          <div v-if="showActions" class="card-actions">
            <slot name="actions" :row="item" />
          </div>
        </div>
      </template>
    </div>

    <!-- 桌面端：表格 -->
    <div v-else class="responsive-table-wrapper">
      <el-table
        :data="data"
        :stripe="stripe"
        :border="border"
        :empty-text="emptyText"
        :row-key="rowKey"
        style="width: 100%"
        :header-cell-style="headerCellStyle"
        @row-click="handleRowClick"
        v-bind="$attrs"
      >
        <el-table-column
          v-for="column in columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :sortable="column.sortable"
          :align="column.align || 'left'"
          :fixed="column.fixed"
          :show-overflow-tooltip="column.showOverflowTooltip"
        >
          <template #default="scope">
            <slot :name="column.prop" :row="scope.row">
              {{ formatValue(scope.row, column) }}
            </slot>
          </template>
        </el-table-column>
        <el-table-column
          v-if="showActions"
          :label="actionsLabel"
          :width="actionsWidth"
          :min-width="actionsMinWidth"
          fixed="right"
          align="left"
        >
          <template #default="scope">
            <slot name="actions" :row="scope.row" />
          </template>
        </el-table-column>
        <slot name="extra-columns" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

export interface ResponsiveTableColumn {
  prop: string
  label: string
  width?: string | number
  minWidth?: string | number
  sortable?: boolean | 'custom'
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  showOverflowTooltip?: boolean
  formatter?: (row: any, column: ResponsiveTableColumn) => string
}

const props = withDefaults(defineProps<{
  data: any[]
  columns: ResponsiveTableColumn[]
  loading?: boolean
  stripe?: boolean
  border?: boolean
  emptyText?: string
  rowKey?: string | ((row: any) => string)
  showActions?: boolean
  actionsLabel?: string
  actionsWidth?: string | number
  actionsMinWidth?: string | number
  headerCellStyle?: Record<string, string | number>
}>(), {
  loading: false,
  stripe: true,
  border: true,
  emptyText: '暂无数据',
  showActions: false,
  actionsLabel: '操作',
  actionsWidth: 120,
  headerCellStyle: () => ({ background: '#f5f7fa', color: '#303133', fontWeight: 600 }),
})

const emit = defineEmits<{
  (e: 'row-click', row: any): void
}>()

const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
}

function getRowKey(row: any): string {
  if (typeof props.rowKey === 'function') {
    return props.rowKey(row)
  }
  if (typeof props.rowKey === 'string') {
    return String(row[props.rowKey])
  }
  return String(row.id) || String(row._id) || String(Math.random())
}

function formatValue(row: any, column: ResponsiveTableColumn): string {
  if (column.formatter) {
    return column.formatter(row, column)
  }
  const value = row[column.prop]
  if (value === null || value === undefined || value === '') return '-'
  return String(value)
}

function handleCardClick(row: any) {
  emit('row-click', row)
}

function handleRowClick(row: any) {
  emit('row-click', row)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.responsive-table-root {
  width: 100%;
}

.responsive-table-wrapper {
  width: 100%;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.responsive-table-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.responsive-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
}

.responsive-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}

.card-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.card-row:last-of-type {
  border-bottom: none;
}

.card-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.card-value {
  flex: 1;
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  text-align: right;
  min-width: 0;
  word-break: break-word;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e8e8e8;
}

.card-actions :deep(.el-button) {
  flex: 1 1 calc(50% - 4px);
  min-width: calc(50% - 4px);
  min-height: 36px;
  font-size: 13px;
  margin: 0;
}
</style>
