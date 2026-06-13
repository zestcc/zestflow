package com.zestflow.admin.client.cache;

import java.util.Optional;

/**
 * Executor GET 代理快照缓存 SPI。
 */
public interface ExecutorReadCache {

    boolean isEnabled();

    Optional<Entry> get(String cacheKey);

    void put(String cacheKey, String json);

    /** 写操作成功后按 appCode 前缀失效 */
    void invalidateApp(String appCode);

    static String buildKey(String appCode, String path, String query) {
        return appCode + "|" + path + "|" + (query == null ? "" : query);
    }

    record Entry(String json, long cachedAtMs) {
    }
}
