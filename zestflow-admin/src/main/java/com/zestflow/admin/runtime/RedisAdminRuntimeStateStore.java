package com.zestflow.admin.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainSyncDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(AdminDeployModeConditions.Cluster.class)
public class RedisAdminRuntimeStateStore implements AdminRuntimeStateStore {

    private static final String PUBLISH_KEY_PREFIX = "zestflow:admin:publish:";
    private static final String SYNC_KEY_PREFIX = "zestflow:admin:chain-sync:";
    private static final String SYNC_INDEX_KEY = "zestflow:admin:chain-sync:index";

    @Qualifier("adminRuntimeStringRedisTemplate")
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AdminRuntimeStateProperties properties;

    @Override
    public void savePublishProgress(String chainCode, int success, int total) {
        String key = PUBLISH_KEY_PREFIX + chainCode;
        redisTemplate.opsForValue().set(key, success + "," + total, ttl());
    }

    @Override
    public Optional<int[]> getPublishProgress(String chainCode) {
        String value = redisTemplate.opsForValue().get(PUBLISH_KEY_PREFIX + chainCode);
        if (value == null || !value.contains(",")) {
            return Optional.empty();
        }
        String[] parts = value.split(",", 2);
        return Optional.of(new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
    }

    @Override
    public void saveChainSync(ChainSyncDTO sync) {
        try {
            String key = SYNC_KEY_PREFIX + sync.getExecutorId();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(sync), ttl());
            redisTemplate.opsForSet().add(SYNC_INDEX_KEY, sync.getExecutorId());
            redisTemplate.expire(SYNC_INDEX_KEY, ttl());
        } catch (JsonProcessingException e) {
            log.warn("序列化链同步状态失败 executorId={}", sync.getExecutorId(), e);
        }
    }

    @Override
    public Optional<ChainSyncDTO> getChainSync(String executorId) {
        String json = redisTemplate.opsForValue().get(SYNC_KEY_PREFIX + executorId);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ChainSyncDTO.class));
        } catch (JsonProcessingException e) {
            log.warn("反序列化链同步状态失败 executorId={}", executorId, e);
            return Optional.empty();
        }
    }

    @Override
    public Map<String, ChainSyncDTO> getAllChainSync() {
        Set<String> ids = redisTemplate.opsForSet().members(SYNC_INDEX_KEY);
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<String, ChainSyncDTO> result = new HashMap<>();
        for (String id : ids) {
            getChainSync(id).ifPresent(sync -> result.put(id, sync));
        }
        return result;
    }

    @Override
    public void evictStaleChainSync(long cutoffTimestampMs) {
        Set<String> ids = redisTemplate.opsForSet().members(SYNC_INDEX_KEY);
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            getChainSync(id).ifPresentOrElse(sync -> {
                if (sync.getTimestamp() != null && sync.getTimestamp() < cutoffTimestampMs) {
                    redisTemplate.delete(SYNC_KEY_PREFIX + id);
                    redisTemplate.opsForSet().remove(SYNC_INDEX_KEY, id);
                }
            }, () -> redisTemplate.opsForSet().remove(SYNC_INDEX_KEY, id));
        }
    }

    private Duration ttl() {
        return Duration.ofSeconds(Math.max(60, properties.getTtlSeconds()));
    }
}
