<template>
  <div class="sidebar">
    <div class="logo">
      <img v-if="!collapsed" src="/logo.svg" alt="ZestFlow" class="logo-img" />
      <img v-else src="/favicon.svg" alt="ZF" class="logo-icon" />
    </div>
    <el-menu
      class="sidebar-menu sidebar-scroll"
      :default-active="route.path"
      :collapse="collapsed"
      background-color="transparent"
      text-color="var(--sidebar-text)"
      active-text-color="#ffffff"
      router
      @select="handleMenuSelect"
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <span>{{ $t('layout.dashboard') }}</span>
      </el-menu-item>
      <el-menu-item index="/chains">
        <el-icon><Connection /></el-icon>
        <span>{{ $t('layout.chains') }}</span>
      </el-menu-item>
      <el-menu-item index="/design">
        <el-icon><EditPen /></el-icon>
        <span>{{ $t('layout.design') }}</span>
      </el-menu-item>
      <el-menu-item index="/design/ai-templates">
        <el-icon><MagicStick /></el-icon>
        <span>{{ $t('ai.templates.menu') }}</span>
      </el-menu-item>
      <el-sub-menu index="/playground" v-if="appStore.playgroundEnabled">
        <template #title>
          <el-icon><VideoPlay /></el-icon>
          <span>{{ $t('layout.playgroundMenu') }}</span>
        </template>
        <el-menu-item index="/playground/scenes">
          <el-icon><Collection /></el-icon>
          <span>{{ $t('layout.playgroundScenes') }}</span>
        </el-menu-item>
        <el-menu-item index="/playground/records">
          <el-icon><Document /></el-icon>
          <span>{{ $t('layout.playgroundRecords') }}</span>
        </el-menu-item>
        <el-menu-item index="/playground">
          <el-icon><MagicStick /></el-icon>
          <span>{{ $t('layout.playground') }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item index="/components">
        <el-icon><Grid /></el-icon>
        <span>{{ $t('layout.components') }}</span>
      </el-menu-item>
      <el-menu-item index="/executors">
        <el-icon><Monitor /></el-icon>
        <span>{{ $t('layout.executors') }}</span>
      </el-menu-item>
      <el-menu-item index="/collectors">
        <el-icon><Collection /></el-icon>
        <span>{{ $t('layout.collectors') }}</span>
      </el-menu-item>
      <el-menu-item index="/schedules">
        <el-icon><Timer /></el-icon>
        <span>{{ $t('layout.schedules') }}</span>
      </el-menu-item>
      <el-menu-item index="/logs">
        <el-icon><Document /></el-icon>
        <span>{{ $t('layout.logs') }}</span>
      </el-menu-item>
      <el-sub-menu index="/settings">
        <template #title>
          <el-icon><Tools /></el-icon>
          <span>{{ $t('layout.settings') }}</span>
        </template>
        <el-menu-item index="/settings/profile">
          <el-icon><User /></el-icon>
          <span>{{ $t('layout.profile') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/users">
          <el-icon><UserFilled /></el-icon>
          <span>{{ $t('settings.userManage') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/dict-types">
          <el-icon><List /></el-icon>
          <span>{{ $t('layout.dictTypes') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/sys-config">
          <el-icon><Setting /></el-icon>
          <span>{{ $t('layout.sysConfig') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/tenants">
          <el-icon><Collection /></el-icon>
          <span>{{ $t('tenant.title') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/ai">
          <el-icon><MagicStick /></el-icon>
          <span>{{ $t('settings.ai.menu') }}</span>
        </el-menu-item>
        <el-menu-item index="/settings/alerts">
          <el-icon><Bell /></el-icon>
          <span>{{ $t('settings.alert.menu') }}</span>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import {
  Odometer, Connection, EditPen, VideoPlay, Grid, Timer, Document, Monitor, Collection, User, UserFilled, Tools, List, MagicStick, Bell, Setting,
} from '@element-plus/icons-vue'

defineProps<{ collapsed: boolean }>()
const emit = defineEmits<{ navigate: [index: string] }>()

const route = useRoute()
const appStore = useAppStore()

function handleMenuSelect(index: string) {
  emit('navigate', index)
}

onMounted(() => {
  appStore.fetchFeatures()
})
</script>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.logo {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-img {
  height: 30px;
}

.logo-icon {
  height: 26px;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  padding: 8px 0 12px;
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 100%;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  margin: 2px 10px;
  border-radius: 8px;
  height: 44px;
  line-height: 44px;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.28) 0%, rgba(64, 158, 255, 0.12) 100%) !important;
  color: #ffffff !important;
  font-weight: 500;
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

.sidebar-menu :deep(.el-sub-menu .el-menu-item) {
  margin-left: 10px;
  margin-right: 10px;
  min-width: auto;
}
</style>
