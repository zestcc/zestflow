<template>
  <el-drawer v-model="visible" :title="$t('chains.chainDetails')" :size="480" destroy-on-close append-to-body>
    <div v-if="loading" style="text-align:center;padding:40px">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    </div>
    <template v-else-if="detail">
      <div style="padding:0 8px">
        <div style="font-size:20px;font-weight:600;color:#303133;margin-bottom:12px">{{ detail.name }}</div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('chains.code')">
            <el-tag size="small" style="font-family:monospace">{{ detail.code }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.chainKey')">
            <span v-if="detail.chainKey">{{ detail.chainKey }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.appDeclared')">
            <el-tag v-if="detail.appDeclared" type="warning" size="small">{{ $t('chains.appDeclaredTag') }}</el-tag>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.status')">
            <el-tag :type="statusTagType(detail.status)" size="small">
              {{ statusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.app')">
            {{ detail.appCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.description')">
            {{ detail.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('chains.designName')">
            <span
              v-if="detail.designCode"
              class="code-link"
              @click="openDesignDetail(detail.designCode, detail.appCode!)"
            >{{ detail.designCode }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.createdAt')">{{ detail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedBy')">{{ detail.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('chains.updatedAt')">{{ detail.updatedAt?.replace('T', ' ') }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
    <el-empty v-else :description="$t('common.requestFailed')" />
  </el-drawer>

  <DesignDetailDrawer ref="designDetailDrawerRef" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import { chainApi, type ChainVO } from '@/api/chain'
import DesignDetailDrawer from '@/components/DesignDetailDrawer.vue'

const { t } = useI18n()

const visible = ref(false)
const loading = ref(false)
const detail = ref<ChainVO | null>(null)
const designDetailDrawerRef = ref<InstanceType<typeof DesignDetailDrawer> | null>(null)

function statusTagType(status: number): string {
  return ['danger', 'info', 'warning', 'primary', 'success'][status] || 'info'
}

function statusLabel(status: number): string {
  const labels = [
    t('chains.disabled'),
    t('chains.notDesigned'),
    t('chains.unpublished'),
    t('chains.publishing'),
    t('chains.published'),
  ]
  return labels[status] || '-'
}

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

function openDesignDetail(designCode: string, appCode: string) {
  designDetailDrawerRef.value?.open(designCode, appCode)
}

defineExpose({ open })
</script>
