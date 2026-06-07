import { computed, watch, type MaybeRefOrGetter } from 'vue'
import { toValue } from 'vue'
import { useDictStore } from '@/stores/dict'
import type { DictDataVO } from '@/api/dict'

function cacheKey(typeCode: string, parentTypeCode?: string, parentValue?: string) {
  return `${typeCode}|${parentTypeCode ?? ''}|${parentValue ?? ''}`
}

/** 级联字典：parentTypeCode + parentValue 变化时自动刷新 */
export function useDictCascade(
  typeCode: MaybeRefOrGetter<string>,
  parentTypeCode?: MaybeRefOrGetter<string | undefined>,
  parentValue?: MaybeRefOrGetter<string | undefined>,
) {
  const store = useDictStore()

  const options = computed<DictDataVO[]>(() => {
    const code = toValue(typeCode)
    const pType = toValue(parentTypeCode)
    const pVal = toValue(parentValue)
    const key = cacheKey(code, pType, pVal)
    void store.versions[key]
    return store.cache[key] ?? []
  })

  async function refresh(force = false) {
    const code = toValue(typeCode)
    const pType = toValue(parentTypeCode)
    const pVal = toValue(parentValue)
    await store.loadDict(code, force, pType, pVal)
  }

  watch(
    () => [toValue(typeCode), toValue(parentTypeCode), toValue(parentValue)] as const,
    () => {
      void refresh(true)
    },
    { immediate: true },
  )

  return { options, refresh }
}
