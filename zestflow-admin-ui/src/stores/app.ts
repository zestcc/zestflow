import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFeatures } from '@/api/system'
import { executorApi } from '@/api/executor'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const playgroundEnabled = ref(false)
  const hasOnlineApps = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  async function fetchFeatures() {
    try {
      const res: any = await getFeatures()
      playgroundEnabled.value = res.playground?.enabled === true
    } catch {
      playgroundEnabled.value = false
    }
  }

  async function fetchOnlineApps() {
    try {
      const apps = await executorApi.listApps(true)
      hasOnlineApps.value = apps.length > 0
    } catch {
      hasOnlineApps.value = false
    }
  }

  return { sidebarCollapsed, playgroundEnabled, hasOnlineApps, toggleSidebar, fetchFeatures, fetchOnlineApps }
})
