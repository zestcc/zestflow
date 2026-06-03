<template>
  <el-drawer v-model="visible" :title="$t('chains.chainDetails')" :size="480" destroy-on-close>
    <div v-if="loading" style="text-align:center;padding:40px">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    </div>
    <template v-else-if="detail">
      <div style="padding:0 8px">
        <div style="font-size:20px;font-weight:600;color:#303133;margin-bottom:12px">{{ detail.name }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('logs.chainCode')">
            <el-tag size="small" style="font-family:monospace">{{ detail.code }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.status')">
            <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
              {{ detail.status === 1 ? $t('chains.enabled') : $t('chains.disabled') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.app')">
            {{ detail.appCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.description')">
            {{ detail.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.createdAt')">{{ detail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
    <el-empty v-else :description="$t('common.requestFailed')" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { chainApi, type ChainVO } from '@/api/chain'

const visible = ref(false)
const loading = ref(false)
const detail = ref<ChainVO | null>(null)

async function open(chainCode: string, appCode: string) {
  if (!chainCode || !appCode) return
  visible.value = true
  loading.value = true
  detail.value = null
  try {
    detail.value = await chainApi.getByCode(chainCode, appCode)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
