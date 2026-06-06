package com.zestflow.admin.runtime.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.schedule.platform.PlatformJobKeys;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterChainSyncCacheMonitor {

    private static final String LOCK_NAME = "zestflow-admin-chain-sync-cache-evict";

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void evictStaleSyncStatus() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.CHAIN_SYNC_CACHE_EVICT);
    }
}
