<template>
  <el-container class="layout-container">
    <!-- 移动端：侧边栏作为 overlay drawer -->
    <el-drawer
      v-if="isMobile"
      v-model="sidebarOpen"
      :size="260"
      :with-header="false"
      :close-on-click-modal="true"
      direction="ltr"
    >
      <AppSidebar :collapsed="false" />
    </el-drawer>

    <!-- 桌面端：固定侧边栏 -->
    <el-aside v-else :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="desktop-sidebar">
      <AppSidebar :collapsed="appStore.sidebarCollapsed" />
    </el-aside>

    <el-container class="main-container">
      <el-header>
        <AppHeader
          :is-mobile="isMobile"
          @toggle-sidebar="toggleMobileSidebar"
        />
      </el-header>
      <el-main class="main-content">
        <NoAppEmpty v-if="route.meta?.requiresExecutor && !appStore.hasOnlineApps" />
        <template v-else>
          <div v-if="!route.meta?.hideTitle" class="page-title">{{ route.meta?.title || '' }}</div>
          <router-view />
        </template>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import NoAppEmpty from '@/components/NoAppEmpty.vue'

const route = useRoute()
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'

const appStore = useAppStore()
const userStore = useUserStore()

const isMobile = ref(false)
const sidebarOpen = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    sidebarOpen.value = false
  }
}

function toggleMobileSidebar() {
  if (isMobile.value) {
    sidebarOpen.value = !sidebarOpen.value
  } else {
    appStore.toggleSidebar()
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  userStore.getUserInfo()
  appStore.fetchOnlineApps()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.desktop-sidebar {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 60px;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

/* ============================================================
   移动端
   ============================================================ */
@media (max-width: 767px) {
  .el-header {
    padding: 0 12px;
    height: 52px;
  }

  .main-content {
    padding: 12px;
  }

  .page-title {
    font-size: 16px;
    margin-bottom: 12px;
    padding-bottom: 8px;
  }
}

/* ============================================================
   平板
   ============================================================ */
@media (min-width: 768px) and (max-width: 1023px) {
  .el-header {
    padding: 0 16px;
  }

  .main-content {
    padding: 16px;
  }
}
</style>