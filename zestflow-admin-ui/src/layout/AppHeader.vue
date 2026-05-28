<template>
  <div class="header">
    <el-button text @click="$emit('toggle-sidebar')">
      <el-icon :size="20">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
    </el-button>
    <div class="header-right">
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
          <span class="user-name">{{ userStore.user?.username || $t('layout.userMenu') }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile')">
              {{ $t('layout.profile') }}
            </el-dropdown-item>
            <el-dropdown-item @click="router.push('/settings')">
              {{ $t('layout.settings') }}
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
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useLocale } from '@/i18n/useLocale'
import { Fold, Expand, ArrowDown } from '@element-plus/icons-vue'

defineProps<{ collapsed?: boolean }>()
defineEmits<{ 'toggle-sidebar': [] }>()

const router = useRouter()
const userStore = useUserStore()
const { locale, setLocale } = useLocale()

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
  font-size: 14px;
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
</style>
