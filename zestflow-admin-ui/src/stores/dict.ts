import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { dictApi, type DictDataVO } from '@/api/dict'

function cacheKey(typeCode: string, parentTypeCode?: string, parentValue?: string) {
  return `${typeCode}|${parentTypeCode ?? ''}|${parentValue ?? ''}`
}

export const useDictStore = defineStore('dict', () => {
  const cache = reactive<Record<string, DictDataVO[]>>({})
  const versions = reactive<Record<string, number>>({})
  const loading = reactive<Record<string, boolean>>({})

  async function loadDict(
    typeCode: string,
    force = false,
    parentTypeCode?: string,
    parentValue?: string,
  ): Promise<DictDataVO[]> {
    const key = cacheKey(typeCode, parentTypeCode, parentValue)
    if (!force && key in cache) {
      return cache[key]
    }
    loading[key] = true
    try {
      const data = await dictApi.getDictData(typeCode, {
        parentTypeCode: parentTypeCode || undefined,
        parentValue: parentValue || undefined,
      })
      cache[key] = data ?? []
      return cache[key]
    } catch {
      if (!(key in cache)) {
        cache[key] = []
      }
      return cache[key]
    } finally {
      loading[key] = false
    }
  }

  function invalidate(typeCode: string) {
    const prefix = `${typeCode}|`
    for (const key of Object.keys(cache)) {
      if (key.startsWith(prefix)) {
        delete cache[key]
        versions[key] = (versions[key] ?? 0) + 1
      }
    }
  }

  function invalidateAll() {
    for (const key of Object.keys(cache)) {
      delete cache[key]
    }
    for (const key of Object.keys(versions)) {
      versions[key] = (versions[key] ?? 0) + 1
    }
  }

  return { cache, versions, loading, loadDict, invalidate, invalidateAll }
})
