<template>
  <!-- 移动端：卡片列表 -->
  <div class="responsive-table-cards" v-if="isMobile">
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
        <span class="card-value">{{ formatValue(item, column) }}</span>
      </div>
      <div v-if="showActions" class="card-actions">
        <slot name="actions" :row="item"></slot>
      </div>
    </div>
  </div>

  <!-- 桌面端：表格 -->
  <div class="responsive-table-wrapper" v-else>
    <el-table
      :data="data"
      :stripe="stripe"
      :border="border"
      :empty-text="emptyText"
      :row-key="rowKey"
      @row-click="handleRowClick"
      v-bind="$attrs"
    >
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :sortable="column.sortable"
        :align="column.align || 'left'"
      >
        <template #default="scope">
          <slot :name="column.prop" :row="scope.row">
            {{ formatValue(scope.row, column) }}
          </slot>
        </template>
      </el-table-column>
      <slot name="extra-columns"></slot>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

interface Column {
  prop: string
  label: string
  width?: string | number
  sortable?: boolean
  align?: string
  formatter?: (row: any, column: Column) => string
}

const props = defineProps<{
  data: any[]
  columns: Column[]
  stripe?: boolean
  border?: boolean
  emptyText?: string
  rowKey?: string | ((row: any) => string)
  showActions?: boolean
}>()

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

function formatValue(row: any, column: Column): string {
  if (column.formatter) {
    return column.formatter(row, column)
  }
  const value = row[column.prop]
  if (value === null || value === undefined) return '-'
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
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.card-row:last-of-type {
  border-bottom: none;
}

.card-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.card-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  text-align: right;
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e8e8e8;
}

.card-actions .el-button {
  flex: 1;
  height: 36px;
  font-size: 13px;
}
</style>