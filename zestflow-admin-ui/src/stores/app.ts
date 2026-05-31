import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFeatures } from '@/api/system'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const demoEnabled = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  async function fetchFeatures() {
    try {
      const res: any = await getFeatures()
      demoEnabled.value = res.demo?.enabled === true
    } catch {
      demoEnabled.value = false
    }
  }

  return { sidebarCollapsed, demoEnabled, toggleSidebar, fetchFeatures }
})
