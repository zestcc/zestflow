package com.zestflow.admin.registry;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 集群 Admin — Redis 共享存活时间戳，多副本离线判定一致。
 */
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class RedisRegistryLiveStore implements RegistryLiveStore {

    private static final String EXECUTOR_KEY_PREFIX = "zestflow:registry:live:executor:";
    private static final String COLLECTOR_KEY_PREFIX = "zestflow:registry:live:collector:";
    private static final String EXECUTOR_INDEX = "zestflow:registry:live:executor:index";
    private static final String COLLECTOR_INDEX = "zestflow:registry:live:collector:index";

    @Qualifier("adminRuntimeStringRedisTemplate")
    private final StringRedisTemplate redisTemplate;

    @Override
    public void touchExecutor(String executorId) {
        touch(EXECUTOR_KEY_PREFIX, EXECUTOR_INDEX, executorId);
    }

    @Override
    public void touchCollector(String collectorId) {
        touch(COLLECTOR_KEY_PREFIX, COLLECTOR_INDEX, collectorId);
    }

    @Override
    public void removeExecutor(String executorId) {
        remove(EXECUTOR_KEY_PREFIX, EXECUTOR_INDEX, executorId);
    }

    @Override
    public void removeCollector(String collectorId) {
        remove(COLLECTOR_KEY_PREFIX, COLLECTOR_INDEX, collectorId);
    }

    @Override
    public void seedExecutor(String executorId, long lastSeenEpochMs) {
        seed(EXECUTOR_KEY_PREFIX, EXECUTOR_INDEX, executorId, lastSeenEpochMs);
    }

    @Override
    public void seedCollector(String collectorId, long lastSeenEpochMs) {
        seed(COLLECTOR_KEY_PREFIX, COLLECTOR_INDEX, collectorId, lastSeenEpochMs);
    }

    @Override
    public boolean isExecutorAlive(String executorId) {
        return isAlive(readEpoch(EXECUTOR_KEY_PREFIX + executorId));
    }

    @Override
    public boolean isCollectorAlive(String collectorId) {
        return isAlive(readEpoch(COLLECTOR_KEY_PREFIX + collectorId));
    }

    @Override
    public boolean tracksExecutor(String executorId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(EXECUTOR_KEY_PREFIX + executorId));
    }

    @Override
    public boolean tracksCollector(String collectorId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(COLLECTOR_KEY_PREFIX + collectorId));
    }

    @Override
    public OptionalLong executorLastSeenEpochMs(String executorId) {
        return readEpoch(EXECUTOR_KEY_PREFIX + executorId);
    }

    @Override
    public OptionalLong collectorLastSeenEpochMs(String collectorId) {
        return readEpoch(COLLECTOR_KEY_PREFIX + collectorId);
    }

    @Override
    public Set<String> aliveExecutorIds() {
        return aliveIds(EXECUTOR_KEY_PREFIX, EXECUTOR_INDEX);
    }

    @Override
    public Set<String> aliveCollectorIds() {
        return aliveIds(COLLECTOR_KEY_PREFIX, COLLECTOR_INDEX);
    }

    private void touch(String prefix, String indexKey, String id) {
        String key = prefix + id;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()), redisTtl());
        redisTemplate.opsForSet().add(indexKey, id);
        redisTemplate.expire(indexKey, redisTtl());
    }

    private void seed(String prefix, String indexKey, String id, long epochMs) {
        String key = prefix + id;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        redisTemplate.opsForValue().set(key, String.valueOf(epochMs), redisTtl());
        redisTemplate.opsForSet().add(indexKey, id);
        redisTemplate.expire(indexKey, redisTtl());
    }

    private void remove(String prefix, String indexKey, String id) {
        redisTemplate.delete(prefix + id);
        redisTemplate.opsForSet().remove(indexKey, id);
    }

    private Set<String> aliveIds(String prefix, String indexKey) {
        Set<String> ids = redisTemplate.opsForSet().members(indexKey);
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        long deadline = System.currentTimeMillis() - RegistryConstants.deadTimeoutMillis();
        return ids.stream()
                .filter(id -> readEpoch(prefix + id).orElse(0L) >= deadline)
                .collect(Collectors.toUnmodifiableSet());
    }

    private OptionalLong readEpoch(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static boolean isAlive(OptionalLong epoch) {
        return epoch.isPresent()
                && System.currentTimeMillis() - epoch.getAsLong() < RegistryConstants.deadTimeoutMillis();
    }

    private static Duration redisTtl() {
        return Duration.ofMillis(RegistryConstants.deadTimeoutMillis() * 2);
    }
}
