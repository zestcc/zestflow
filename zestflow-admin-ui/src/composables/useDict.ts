import { computed, onActivated, onMounted, watch } from 'vue'
import { useDictStore } from '@/stores/dict'

function cacheKey(typeCode: string) {
  return `${typeCode}||`
}

/** 加载字典并在全局失效后自动刷新（供下拉选项等场景使用） */
export function useDict(typeCode: string) {
  const store = useDictStore()

  const options = computed(() => store.cache[cacheKey(typeCode)] ?? [])

  async function refresh(force = false) {
    await store.loadDict(typeCode, force)
  }

  function reloadOnEnter() {
    void refresh(true)
  }

  onMounted(reloadOnEnter)

  onActivated(reloadOnEnter)

  watch(
    () => store.versions[cacheKey(typeCode)],
    () => {
      void refresh(true)
    },
  )

  return { options, refresh }
}
