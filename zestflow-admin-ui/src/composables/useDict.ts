import { computed, onMounted, watch } from 'vue'
import { useDictStore } from '@/stores/dict'

/** 加载字典并在全局失效后自动刷新（供下拉选项等场景使用） */
export function useDict(typeCode: string) {
  const store = useDictStore()

  const options = computed(() => store.cache[typeCode] ?? [])

  async function refresh(force = false) {
    await store.loadDict(typeCode, force)
  }

  onMounted(() => {
    void refresh()
  })

  watch(
    () => store.versions[typeCode],
    () => {
      void refresh(true)
    },
  )

  return { options, refresh }
}
