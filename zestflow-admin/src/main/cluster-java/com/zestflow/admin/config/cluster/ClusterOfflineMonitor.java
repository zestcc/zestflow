package com.zestflow.admin.config.cluster;

import com.zestflow.admin.config.OfflineMonitorService;
import com.zestflow.admin.runtime.AdminDeployModeConditions;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集群 Admin 离线检测 — ShedLock 保证多副本仅一个节点执行。
 */
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterOfflineMonitor {

    private static final String LOCK_CHECK = "zestflow-admin-offline-check";
    private static final String LOCK_CLEANUP = "zestflow-admin-offline-cleanup";

    private final OfflineMonitorService offlineMonitorService;

    @Scheduled(fixedRate = 30_000)
    @SchedulerLock(name = LOCK_CHECK, lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void checkOffline() {
        offlineMonitorService.checkOffline();
    }

    @Scheduled(fixedRate = 1_800_000)
    @SchedulerLock(name = LOCK_CLEANUP, lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupStaleAbnormal() {
        offlineMonitorService.cleanupStaleAbnormal();
    }
}
