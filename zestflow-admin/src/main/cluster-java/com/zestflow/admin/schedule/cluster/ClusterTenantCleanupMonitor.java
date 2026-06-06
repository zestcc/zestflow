package com.zestflow.admin.schedule.cluster;

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
public class ClusterTenantCleanupMonitor {

    private static final String LOCK_TRIAL = "zestflow-admin-tenant-trial-cleanup";

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = LOCK_TRIAL, lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void cleanupTrialTenants() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.TENANT_CLEANUP);
    }
}
