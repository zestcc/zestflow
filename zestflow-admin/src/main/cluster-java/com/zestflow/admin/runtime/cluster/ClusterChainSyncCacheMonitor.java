package com.zestflow.admin.runtime.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.runtime.ChainSyncCacheEvictor;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集群 Admin 链同步缓存清理 — ShedLock 保证多副本仅一个节点执行。
 */
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterChainSyncCacheMonitor {

    private static final String LOCK_NAME = "zestflow-admin-chain-sync-evict";

    private final ChainSyncCacheEvictor chainSyncCacheEvictor;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void evictStaleSyncStatus() {
        chainSyncCacheEvictor.evictStale();
    }
}
