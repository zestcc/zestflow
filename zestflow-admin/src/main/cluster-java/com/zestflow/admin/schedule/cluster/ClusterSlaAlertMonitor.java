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
public class ClusterSlaAlertMonitor {

    private static final String LOCK_NAME = "zestflow-admin-sla-alert-scan";

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRateString = "${zestflow.alert.scan-interval-ms:300000}")
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT30S"
    )
    public void scan() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.EXECUTION_SLA_ALERT);
    }
}
