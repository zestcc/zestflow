<template>
  <el-drawer
    v-model="visible"
    :title="$t('design.detail')"
    :size="drawerSize"
    class="detail-drawer"
    destroy-on-close
    append-to-body
  >
    <div v-if="loading" style="text-align:center;padding:40px">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    </div>
    <template v-else-if="detail">
      <div class="detail-drawer-body">
        <div class="detail-drawer-header">
          <div class="detail-drawer-title">{{ detail.name }}</div>
          <el-button type="primary" size="small" @click="goEdit">
            {{ $t('design.editDesign') }}
          </el-button>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('design.code')">
            <el-tag size="small" style="font-family:monospace">{{ detail.code }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.status')">
            <el-tag :type="enableStatusTagType(detail.status)" size="small">
              {{ enableStatusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.designer')">
            {{ detail.designer || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.app')">
            {{ detail.appCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.description')">
            {{ detail.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.boundChainCodes')">
            <template v-if="detail.boundChains && detail.boundChains.length > 0">
              <div
                v-for="c in detail.boundChains"
                :key="c.code"
                style="display:flex;align-items:center;gap:6px;margin-bottom:4px"
              >
                <el-tag size="small" type="info" style="font-family:monospace">{{ c.code }}</el-tag>
                <span style="font-size:13px;color:#303133">{{ c.name }}</span>
              </div>
            </template>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('design.createdAt')">{{ detail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedBy')">{{ detail.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('design.updatedAt')">{{ detail.updatedAt?.replace('T', ' ') }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </template>
    <el-empty v-else :description="$t('common.requestFailed')" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { designApi, type DesignVO } from '@/api/design'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { useDictLabel } from '@/composables/useDictLabel'

const router = useRouter()
const { drawerSize } = useResponsiveDrawerSize(520)
const { labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')

const visible = ref(false)
const loading = ref(false)
const detail = ref<DesignVO | null>(null)

async function open(designCode: string, appCode: string) {
  if (!designCode || !appCode) return
  visible.value = true
  loading.value = true
  detail.value = null
  try {
    detail.value = await designApi.getByCode(designCode, appCode)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function goEdit() {
  if (!detail.value?.code || !detail.value.appCode) return
  router.push({
    name: 'DesignEditor',
    params: { id: detail.value.code },
    query: { appCode: detail.value.appCode },
  })
}

defineExpose({ open })
</script>

<style scoped>
.detail-drawer-body {
  padding: 0 4px;
}

.detail-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-drawer-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
</style>
