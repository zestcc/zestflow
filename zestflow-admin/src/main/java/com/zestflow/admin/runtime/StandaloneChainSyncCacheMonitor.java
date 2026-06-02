package com.zestflow.admin.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单机部署链同步缓存清理 — {@code deploy-mode=standalone} 时启用，无 ShedLock。
 */
@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneChainSyncCacheMonitor {

    private final ChainSyncCacheEvictor chainSyncCacheEvictor;

    @Scheduled(fixedRate = 60_000)
    public void evictStaleSyncStatus() {
        chainSyncCacheEvictor.evictStale();
    }
}
