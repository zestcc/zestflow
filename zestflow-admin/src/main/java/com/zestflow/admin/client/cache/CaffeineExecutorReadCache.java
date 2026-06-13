package com.zestflow.admin.client.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机 Caffeine 快照 — 默认启用，cluster 可后续换 Redis 实现。
 */
@Slf4j
public class CaffeineExecutorReadCache implements ExecutorReadCache {

    private final Cache<String, Entry> cache;
    private final ConcurrentHashMap<String, Set<String>> keysByApp = new ConcurrentHashMap<>();

    public CaffeineExecutorReadCache(ExecutorReadCacheProperties properties) {
        int ttlMinutes = Math.max(1, properties.getTtlMinutes());
        int maxEntries = Math.max(16, properties.getMaxEntries());
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .build();
        log.info("Executor 读快照缓存已启用 ttl={}min maxEntries={}", ttlMinutes, maxEntries);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<Entry> get(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(cacheKey));
    }

    @Override
    public void put(String cacheKey, String json) {
        if (!StringUtils.hasText(cacheKey) || !StringUtils.hasText(json)) {
            return;
        }
        cache.put(cacheKey, new Entry(json, System.currentTimeMillis()));
        String appCode = extractAppCode(cacheKey);
        if (appCode != null) {
            keysByApp.computeIfAbsent(appCode, k -> ConcurrentHashMap.newKeySet()).add(cacheKey);
        }
    }

    @Override
    public void invalidateApp(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        Set<String> keys = keysByApp.remove(appCode.trim());
        if (keys != null && !keys.isEmpty()) {
            cache.invalidateAll(keys);
        }
    }

    private static String extractAppCode(String cacheKey) {
        int idx = cacheKey.indexOf('|');
        if (idx <= 0) {
            return null;
        }
        return cacheKey.substring(0, idx);
    }
}
