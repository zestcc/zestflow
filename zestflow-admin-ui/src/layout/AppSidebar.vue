<template>
  <div class="sidebar">
    <div class="logo">
      <img v-if="!collapsed" src="/logo.svg" alt="ZestFlow" class="logo-img" />
      <img v-else src="/favicon.svg" alt="ZF" class="logo-icon" />
    </div>
    <el-menu
      :default-active="route.path"
      :collapse="collapsed"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      router
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
      <el-menu-item index="/playground" v-if="appStore.playgroundEnabled">
        <el-icon><VideoPlay /></el-icon>
        <span>{{ $t('layout.playground') }}</span>
      </el-menu-item>
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
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import {
  Odometer, Connection, EditPen, VideoPlay, Grid, Timer, Document, Monitor, Collection, User, UserFilled, Tools, List,
} from '@element-plus/icons-vue'

defineProps<{ collapsed: boolean }>()

const route = useRoute()
const appStore = useAppStore()

onMounted(() => {
  appStore.fetchFeatures()
})
</script>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo-img {
  height: 32px;
}

.logo-icon {
  height: 28px;
}

.el-menu {
  border-right: none;
}
</style>
