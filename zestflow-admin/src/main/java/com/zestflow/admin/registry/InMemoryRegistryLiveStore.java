package com.zestflow.admin.registry;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.common.constant.RegistryConstants;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 单机 Admin — 内存维护 lastSeen，零 DB 依赖心跳路径。
 */
@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
public class InMemoryRegistryLiveStore implements RegistryLiveStore {

    private final ConcurrentHashMap<String, Long> executorLastSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> collectorLastSeen = new ConcurrentHashMap<>();

    @Override
    public void touchExecutor(String executorId) {
        executorLastSeen.put(executorId, System.currentTimeMillis());
    }

    @Override
    public void touchCollector(String collectorId) {
        collectorLastSeen.put(collectorId, System.currentTimeMillis());
    }

    @Override
    public void removeExecutor(String executorId) {
        executorLastSeen.remove(executorId);
    }

    @Override
    public void removeCollector(String collectorId) {
        collectorLastSeen.remove(collectorId);
    }

    @Override
    public void seedExecutor(String executorId, long lastSeenEpochMs) {
        executorLastSeen.putIfAbsent(executorId, lastSeenEpochMs);
    }

    @Override
    public void seedCollector(String collectorId, long lastSeenEpochMs) {
        collectorLastSeen.putIfAbsent(collectorId, lastSeenEpochMs);
    }

    @Override
    public boolean isExecutorAlive(String executorId) {
        return isAlive(executorLastSeen, executorId);
    }

    @Override
    public boolean isCollectorAlive(String collectorId) {
        return isAlive(collectorLastSeen, collectorId);
    }

    @Override
    public boolean tracksExecutor(String executorId) {
        return executorLastSeen.containsKey(executorId);
    }

    @Override
    public boolean tracksCollector(String collectorId) {
        return collectorLastSeen.containsKey(collectorId);
    }

    @Override
    public OptionalLong executorLastSeenEpochMs(String executorId) {
        return optionalEpoch(executorLastSeen.get(executorId));
    }

    @Override
    public OptionalLong collectorLastSeenEpochMs(String collectorId) {
        return optionalEpoch(collectorLastSeen.get(collectorId));
    }

    @Override
    public Set<String> aliveExecutorIds() {
        return aliveIds(executorLastSeen);
    }

    @Override
    public Set<String> aliveCollectorIds() {
        return aliveIds(collectorLastSeen);
    }

    private static boolean isAlive(Map<String, Long> store, String id) {
        Long last = store.get(id);
        if (last == null) {
            return false;
        }
        return System.currentTimeMillis() - last < RegistryConstants.deadTimeoutMillis();
    }

    private static Set<String> aliveIds(Map<String, Long> store) {
        long deadline = System.currentTimeMillis() - RegistryConstants.deadTimeoutMillis();
        return store.entrySet().stream()
                .filter(e -> e.getValue() >= deadline)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static OptionalLong optionalEpoch(Long epoch) {
        return epoch == null ? OptionalLong.empty() : OptionalLong.of(epoch);
    }
}
