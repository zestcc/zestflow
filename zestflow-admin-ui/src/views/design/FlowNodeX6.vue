<template>
  <div class="x6-node" :class="[`type-${nodeType}`, { selected: isSelected }]" :style="nodeStyle">
    <div class="node-header" :style="{ background: headerBg }">
      <div class="node-icon" v-html="icons[nodeType] || '?'" />
      <div class="node-label">{{ nodeData?.label || nodeType }}</div>
    </div>
    <div v-if="nodeData?.description" class="node-body">
      <span class="node-desc">{{ nodeData.description }}</span>
    </div>
    <div class="node-status-bar" :style="{ background: statusBarBg }" />
  </div>
</template>

<script setup lang="ts">
import { inject, ref, computed, onMounted, onBeforeUnmount } from 'vue'

const getNode = inject('getNode') as (() => any) | undefined
const node = getNode?.()
const nodeData = ref(node?.getData() || {})
const isSelected = ref(false)

const nodeType = computed(() => nodeData.value?.nodeType || 'task')

const colors: Record<string, { primary: string; light: string; dark: string; gradient: string }> = {
  start: { primary: '#67c23a', light: '#e8f8e0', dark: '#529b2e', gradient: 'linear-gradient(135deg, #67c23a, #85ce61)' },
  task: { primary: '#409eff', light: '#e6f0ff', dark: '#2a7de1', gradient: 'linear-gradient(135deg, #409eff, #6ab0ff)' },
  condition: { primary: '#e6a23c', light: '#fdf0e0', dark: '#c48a2b', gradient: 'linear-gradient(135deg, #e6a23c, #f0b75e)' },
  end: { primary: '#909399', light: '#f0f0f0', dark: '#73767a', gradient: 'linear-gradient(135deg, #909399, #a8abb0)' },
}

const icons: Record<string, string> = {
  start: '<svg viewBox="0 0 16 16" width="16" height="16"><circle cx="8" cy="8" r="6" fill="currentColor"/></svg>',
  task: '<svg viewBox="0 0 16 16" width="16" height="16"><rect x="2" y="1" width="12" height="14" rx="2" fill="currentColor"/></svg>',
  condition: '<svg viewBox="0 0 16 16" width="16" height="16"><polygon points="8,1 15,8 8,15 1,8" fill="currentColor"/></svg>',
  end: '<svg viewBox="0 0 16 16" width="16" height="16"><circle cx="8" cy="8" r="5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="8" cy="8" r="2" fill="currentColor"/></svg>',
}

const colorConfig = computed(() => colors[nodeType.value] || colors.task)

const headerBg = computed(() => colorConfig.value.gradient)
const statusBarBg = computed(() => colorConfig.value.dark)

const nodeStyle = computed(() => {
  const c = colorConfig.value
  return {
    '--node-border': isSelected.value ? c.primary : '#e8e8e8',
    borderColor: isSelected.value ? c.primary : '#e8e8e8',
    boxShadow: isSelected.value
      ? `0 0 0 2px ${c.primary}30, 0 4px 16px rgba(0,0,0,0.12)`
      : '0 2px 6px rgba(0,0,0,0.08)',
  }
})

const headerHeight = computed(() => nodeData.value?.description ? 32 : 40)

onMounted(() => {
  if (!node) return
  const updateData = () => { nodeData.value = { ...node.getData() } }
  const updateSelected = () => {
    isSelected.value = typeof node.isSelected === 'function' ? node.isSelected() : false
  }
  node.on('change:data', updateData)
  if (typeof node.on === 'function') {
    node.on('change:selected', updateSelected)
  }
  if (typeof node.isSelected === 'function') {
    isSelected.value = node.isSelected()
  }
})

onBeforeUnmount(() => {
  if (!node) return
  node.off('change:data')
  if (typeof node.off === 'function') {
    node.off('change:selected')
  }
})
</script>

<style scoped>
.x6-node {
  width: 100%;
  height: 100%;
  background: #fff;
  border: 2px solid var(--node-border, #e8e8e8);
  border-radius: 10px;
  overflow: hidden;
  transition: box-shadow 0.25s ease, border-color 0.25s ease;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  user-select: none;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.x6-node:hover {
  border-color: var(--node-border, #c0c4cc);
}

.node-header {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  gap: 8px;
  min-height: 32px;
  flex-shrink: 0;
}

.node-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.node-icon :deep(svg) {
  filter: drop-shadow(0 1px 1px rgba(0,0,0,0.2));
}

.node-label {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-shadow: 0 1px 1px rgba(0,0,0,0.15);
}

.node-body {
  padding: 6px 12px 8px;
  flex: 1;
  display: flex;
  align-items: center;
}

.node-desc {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.node-status-bar {
  height: 3px;
  flex-shrink: 0;
}
</style>
