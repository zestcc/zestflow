<template>
  <div class="settings-page">
    <template v-if="userStore.user?.isSuperAdmin === 1">
      <router-view />
    </template>
    <el-alert
      v-else
      :title="$t('settings.superAdminOnly')"
      type="warning"
      show-icon
      :closable="false"
    />
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
</script>

<style scoped>
.settings-page {
  padding: 0;
}
</style>
