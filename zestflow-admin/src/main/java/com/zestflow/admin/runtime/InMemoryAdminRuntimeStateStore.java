package com.zestflow.admin.runtime;

import com.zestflow.common.model.dto.ChainSyncDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
public class InMemoryAdminRuntimeStateStore implements AdminRuntimeStateStore {

    private final ConcurrentHashMap<String, int[]> publishProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChainSyncDTO> chainSyncStatus = new ConcurrentHashMap<>();

    @Override
    public void savePublishProgress(String chainCode, int success, int total) {
        publishProgress.put(chainCode, new int[]{success, total});
    }

    @Override
    public Optional<int[]> getPublishProgress(String chainCode) {
        return Optional.ofNullable(publishProgress.get(chainCode));
    }

    @Override
    public void saveChainSync(ChainSyncDTO sync) {
        chainSyncStatus.put(sync.getExecutorId(), sync);
    }

    @Override
    public Optional<ChainSyncDTO> getChainSync(String executorId) {
        return Optional.ofNullable(chainSyncStatus.get(executorId));
    }

    @Override
    public Map<String, ChainSyncDTO> getAllChainSync() {
        return Map.copyOf(chainSyncStatus);
    }

    @Override
    public void evictStaleChainSync(long cutoffTimestampMs) {
        chainSyncStatus.entrySet().removeIf(e ->
                e.getValue().getTimestamp() != null && e.getValue().getTimestamp() < cutoffTimestampMs);
    }
}
