import type { Ref } from 'vue'

export interface ExecutorReadCacheMeta {
  stale?: boolean
  cachedAt?: number
}

export interface ConsumeReadCacheOptions {
  /** 为 true 时仅在有 stale 时置位，不因单次 fresh 响应清除提示 */
  accumulate?: boolean
}

/** 解析代理接口返回的 _readCache 元数据，供列表页展示离线快照提示 */
export function consumeExecutorReadCacheMeta(
  res: unknown,
  staleRef: Ref<boolean>,
  options?: ConsumeReadCacheOptions,
) {
  if (!res || typeof res !== 'object' || Array.isArray(res)) {
    if (!options?.accumulate) {
      staleRef.value = false
    }
    return res
  }
  const obj = res as Record<string, unknown>
  const meta = obj._readCache as ExecutorReadCacheMeta | undefined
  if (meta?.stale) {
    staleRef.value = true
    delete obj._readCache
  } else if (!options?.accumulate) {
    staleRef.value = false
  }
  return obj
}
