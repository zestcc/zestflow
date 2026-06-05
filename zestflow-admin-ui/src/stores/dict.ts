import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { dictApi, type DictDataVO } from '@/api/dict'

export const useDictStore = defineStore('dict', () => {
  const cache = reactive<Record<string, DictDataVO[]>>({})
  const versions = reactive<Record<string, number>>({})
  const loading = reactive<Record<string, boolean>>({})

  async function loadDict(typeCode: string, force = false): Promise<DictDataVO[]> {
    if (!force && typeCode in cache) {
      return cache[typeCode]
    }
    loading[typeCode] = true
    try {
      const data = await dictApi.getDictData(typeCode)
      cache[typeCode] = data ?? []
      return cache[typeCode]
    } catch {
      if (!(typeCode in cache)) {
        cache[typeCode] = []
      }
      return cache[typeCode]
    } finally {
      loading[typeCode] = false
    }
  }

  /** 字典 CRUD 后调用，已打开页面会通过 version 监听自动重新拉取 */
  function invalidate(typeCode: string) {
    delete cache[typeCode]
    versions[typeCode] = (versions[typeCode] ?? 0) + 1
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
