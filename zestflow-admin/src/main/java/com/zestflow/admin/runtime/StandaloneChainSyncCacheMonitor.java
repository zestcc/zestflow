package com.zestflow.admin.runtime;

import com.zestflow.admin.schedule.platform.PlatformJobKeys;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneChainSyncCacheMonitor {

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRate = 60_000)
    public void evictStaleSyncStatus() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.CHAIN_SYNC_CACHE_EVICT);
    }
}
