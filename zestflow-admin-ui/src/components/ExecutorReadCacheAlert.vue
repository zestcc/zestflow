<template>
  <el-alert
    v-if="stale"
    type="warning"
    :closable="false"
    show-icon
    class="read-cache-alert"
    :title="$t('common.executorReadCacheHint')"
  />
</template>

<script setup lang="ts">
defineProps<{
  stale: boolean
}>()
</script>

<style scoped>
.read-cache-alert {
  margin-bottom: 12px;
}
</style>
