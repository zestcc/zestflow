<template>
  <div class="settings-sso-page">
    <div class="page-header">
      <h3 style="margin:0">{{ $t('settings.sso.title') }}</h3>
      <p class="page-desc">{{ $t('settings.sso.desc') }}</p>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-descriptions v-if="config" :column="1" border size="small">
        <el-descriptions-item :label="$t('common.status')">
          <el-tag :type="config.enabled ? 'success' : 'info'" size="small">
            {{ config.enabled ? $t('settings.sso.enabled') : $t('settings.sso.disabled') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('settings.sso.provider')">{{ config.provider || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('settings.sso.displayName')">{{ config.displayName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('settings.sso.issuer')">{{ config.issuer || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('settings.sso.clientId')">{{ config.clientId || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else :description="$t('settings.sso.loadFailed')" />
    </el-card>

    <el-alert type="info" :closable="false" show-icon style="margin-top:12px">
      {{ $t('settings.sso.hint') }}
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { authApi } from '@/api/auth'

const loading = ref(false)
const config = ref<{
  enabled: boolean
  provider?: string
  displayName?: string
  issuer?: string
  clientId?: string
} | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    config.value = await authApi.getSsoConfig()
  } catch {
    config.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.settings-sso-page {
  padding: 0 4px;
}
.page-desc {
  margin: 8px 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
