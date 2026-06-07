<template>
  <el-container class="layout-container">
    <!-- 移动端：侧边栏作为 overlay drawer -->
    <el-drawer
      v-if="isMobile"
      v-model="sidebarOpen"
      :size="mobileSidebarSize"
      :with-header="false"
      :close-on-click-modal="true"
      direction="ltr"
      class="mobile-sidebar-drawer"
      append-to-body
    >
      <AppSidebar :collapsed="false" @navigate="closeMobileSidebar" />
    </el-drawer>

    <!-- 桌面端：固定侧边栏 -->
    <el-aside v-else :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="desktop-sidebar">
      <AppSidebar :collapsed="appStore.sidebarCollapsed" />
    </el-aside>

    <el-container class="main-container">
      <el-header>
        <AppHeader
          :is-mobile="isMobile"
          :sidebar-open="sidebarOpen"
          :collapsed="appStore.sidebarCollapsed"
          @toggle-sidebar="toggleMobileSidebar"
        />
      </el-header>
      <el-main class="main-content content-scroll">
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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import NoAppEmpty from '@/components/NoAppEmpty.vue'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const isMobile = ref(false)
const sidebarOpen = ref(false)

const mobileSidebarSize = computed(() => (isMobile.value ? 'min(280px, 85vw)' : 260))

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    sidebarOpen.value = false
  }
}

function closeMobileSidebar() {
  sidebarOpen.value = false
}

function toggleMobileSidebar() {
  if (isMobile.value) {
    sidebarOpen.value = !sidebarOpen.value
  } else {
    appStore.toggleSidebar()
  }
}

watch(
  () => route.path,
  () => {
    if (isMobile.value) {
      closeMobileSidebar()
    }
  },
)

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
  background: var(--main-bg);
}

.desktop-sidebar {
  background: linear-gradient(180deg, var(--sidebar-bg) 0%, var(--sidebar-bg-end) 100%);
  transition: width 0.28s ease;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 100%;
  box-shadow: 1px 0 0 rgba(0, 0, 0, 0.06);
}

.el-header {
  background-color: var(--surface-bg);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  padding: 0 20px;
  height: 56px;
  box-shadow: var(--shadow-sm);
}

.main-content {
  background-color: var(--main-bg);
  padding: 20px 24px;
  overflow-y: auto;
}

.page-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 20px;
  letter-spacing: -0.01em;
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
    margin-bottom: 16px;
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