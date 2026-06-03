<template>
  <el-button
    v-if="code"
    type="primary"
    link
    size="small"
    class="code-link"
    @click.stop="emit('click')"
  >
    {{ code }}
  </el-button>
  <span v-else>-</span>
</template>

<script setup lang="ts">
defineProps<{ code?: string | null }>()
const emit = defineEmits<{ click: [] }>()
</script>

<style scoped>
.code-link {
  font-family: monospace;
  font-weight: 600;
  padding: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
