package com.zestflow.admin.schedule.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.schedule.TenantCleanupService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 集群 Admin 租户清理 — ShedLock 保证多副本仅一个节点执行。
 */
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterTenantCleanupMonitor {

    private static final String LOCK_IP = "zestflow-admin-tenant-ip-cleanup";
    private static final String LOCK_STATUS = "zestflow-admin-tenant-status-cleanup";

    private final TenantCleanupService tenantCleanupService;

    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = LOCK_IP, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void cleanupInactiveIpMappings() {
        tenantCleanupService.cleanupInactiveIpMappings();
    }

    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = LOCK_STATUS, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void updateInactiveTenants() {
        tenantCleanupService.updateInactiveTenants();
    }
}
