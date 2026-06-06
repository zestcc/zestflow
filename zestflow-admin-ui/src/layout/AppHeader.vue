<template>
  <div class="header">
    <el-button text @click="$emit('toggle-sidebar')">
      <el-icon :size="20">
        <Fold v-if="showFoldIcon" />
        <Expand v-else />
      </el-icon>
    </el-button>
    <div class="header-right">
      <!-- 租户切换 -->
      <el-dropdown
        v-if="tenantStore.tenants.length > 1"
        trigger="click"
        class="tenant-switch"
        @command="handleSwitchTenant"
      >
        <span class="tenant-trigger">
          <el-icon><HomeFilled /></el-icon>
          <span class="hide-on-mobile">{{ tenantStore.currentTenant?.name || $t('tenant.select') }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="t in tenantStore.tenants"
              :key="t.id"
              :command="t.id"
              :disabled="t.id === tenantStore.currentTenantId"
            >
              <span>{{ t.name }}</span>
              <span class="tenant-code">({{ t.code }})</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-dropdown trigger="click" class="lang-switch">
        <span class="lang-trigger">
            <span>{{ locale === 'zh-CN' ? '中文' : 'EN' }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              :disabled="locale === 'zh-CN'"
              @click="switchLang('zh-CN')"
            >
              {{ $t('layout.zhCN') }}
            </el-dropdown-item>
            <el-dropdown-item
              :disabled="locale === 'en'"
              @click="switchLang('en')"
            >
              {{ $t('layout.en') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown trigger="click">
        <span class="user-info">
          <el-avatar :size="32" :src="avatarUrl" class="user-avatar">
            {{ userStore.user?.username?.charAt(0)?.toUpperCase() }}
          </el-avatar>
          <span class="user-name hide-on-mobile">{{ userStore.user?.username || $t('layout.userMenu') }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/settings/profile')">
              {{ $t('layout.profile') }}
            </el-dropdown-item>
            <el-dropdown-item @click="router.push('/settings/users')">
              {{ $t('settings.userManage') }}
            </el-dropdown-item>
            <el-dropdown-item divided @click="userStore.logout()">
              {{ $t('layout.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useTenantStore } from '@/stores/tenant'
import { useLocale } from '@/i18n/useLocale'
import { Fold, Expand, ArrowDown, HomeFilled } from '@element-plus/icons-vue'

const props = defineProps<{
  collapsed?: boolean
  isMobile?: boolean
  sidebarOpen?: boolean
}>()
defineEmits<{ 'toggle-sidebar': [] }>()

const showFoldIcon = computed(() => {
  if (props.isMobile) {
    return props.sidebarOpen
  }
  return !props.collapsed
})

const router = useRouter()
const userStore = useUserStore()
const tenantStore = useTenantStore()
const { locale, setLocale } = useLocale()
const switching = ref(false)

async function handleSwitchTenant(tenantId: number) {
  if (switching.value) return
  switching.value = true
  try {
    await tenantStore.switchTenant(tenantId)
    ElMessage.success('租户已切换')
    window.location.reload()
  } catch {
    ElMessage.error('租户切换失败')
  } finally {
    switching.value = false
  }
}

const avatarUrl = computed(() => {
  const avatar = userStore.user?.avatar
  if (!avatar) return ''
  if (avatar.startsWith('http')) return avatar
  return avatar
})

function switchLang(lang: string) {
  setLocale(lang)
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.lang-switch {
  cursor: pointer;
}

.lang-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: var(--font-size-base);
}

.tenant-switch {
  cursor: pointer;
}

.tenant-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: var(--font-size-base);
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  transition: all 0.2s;
}

.tenant-trigger:hover {
  border-color: #409eff;
  color: #409eff;
}

.tenant-code {
  color: #909399;
  font-size: 12px;
  margin-left: 2px;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 8px 2px 2px;
  border-radius: 20px;
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.user-avatar {
  flex-shrink: 0;
  border: 2px solid #e8eaed;
  transition: border-color 0.2s;
}

.user-info:hover .user-avatar {
  border-color: #c0c4cc;
}

.user-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 移动端 */
@media (max-width: 767px) {
  .header-right {
    gap: 8px;
  }

  .lang-trigger {
    font-size: var(--font-size-sm);
  }

  .tenant-trigger {
    font-size: var(--font-size-sm);
    padding: 2px 6px;
  }

  .user-info {
    padding: 2px;
    gap: 4px;
  }
}
</style>