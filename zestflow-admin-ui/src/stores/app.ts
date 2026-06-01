import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFeatures } from '@/api/system'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const playgroundEnabled = ref(false)

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

  return { sidebarCollapsed, playgroundEnabled, toggleSidebar, fetchFeatures }
})
