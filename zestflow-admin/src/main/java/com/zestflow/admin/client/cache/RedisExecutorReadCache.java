package com.zestflow.admin.client.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 集群 Redis 快照 — 多 Admin 副本共享 Executor GET 缓存。
 */
@Slf4j
public class RedisExecutorReadCache implements ExecutorReadCache {

    private static final String VALUE_PREFIX = "zestflow:admin:executor-read:";
    private static final String APP_INDEX_PREFIX = "zestflow:admin:executor-read:app:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisExecutorReadCache(StringRedisTemplate redisTemplate, ExecutorReadCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        int ttlMinutes = Math.max(1, properties.getTtlMinutes());
        this.ttl = Duration.ofMinutes(ttlMinutes);
        log.info("Executor 读快照 Redis 缓存已启用 ttl={}min", ttlMinutes);
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
        String raw = redisTemplate.opsForValue().get(valueKey(cacheKey));
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        return deserialize(raw);
    }

    @Override
    public void put(String cacheKey, String json) {
        if (!StringUtils.hasText(cacheKey) || !StringUtils.hasText(json)) {
            return;
        }
        Entry entry = new Entry(json, System.currentTimeMillis());
        try {
            String payload = objectMapper.writeValueAsString(new StoredEntry(entry.json(), entry.cachedAtMs()));
            String redisKey = valueKey(cacheKey);
            redisTemplate.opsForValue().set(redisKey, payload, ttl.toSeconds(), TimeUnit.SECONDS);
            String appCode = extractAppCode(cacheKey);
            if (appCode != null) {
                String indexKey = appIndexKey(appCode);
                redisTemplate.opsForSet().add(indexKey, redisKey);
                redisTemplate.expire(indexKey, ttl.toSeconds(), TimeUnit.SECONDS);
            }
        } catch (JsonProcessingException ex) {
            log.warn("Executor 读快照写入 Redis 失败 key={}", cacheKey, ex);
        }
    }

    @Override
    public void invalidateApp(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        String indexKey = appIndexKey(appCode.trim());
        Set<String> keys = redisTemplate.opsForSet().members(indexKey);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(indexKey);
    }

    private static String valueKey(String cacheKey) {
        return VALUE_PREFIX + cacheKey;
    }

    private static String appIndexKey(String appCode) {
        return APP_INDEX_PREFIX + appCode;
    }

    private static String extractAppCode(String cacheKey) {
        int idx = cacheKey.indexOf('|');
        if (idx <= 0) {
            return null;
        }
        return cacheKey.substring(0, idx);
    }

    private Optional<Entry> deserialize(String raw) {
        try {
            StoredEntry stored = objectMapper.readValue(raw, StoredEntry.class);
            if (!StringUtils.hasText(stored.json())) {
                return Optional.empty();
            }
            return Optional.of(new Entry(stored.json(), stored.cachedAtMs()));
        } catch (Exception ex) {
            log.debug("Executor 读快照 Redis 反序列化失败", ex);
            return Optional.empty();
        }
    }

    private record StoredEntry(String json, long cachedAtMs) {
    }
}
