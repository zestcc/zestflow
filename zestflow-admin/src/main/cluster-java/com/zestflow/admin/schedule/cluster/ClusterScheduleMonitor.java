package com.zestflow.admin.schedule.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.schedule.platform.PlatformJobKeys;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Conditional(AdminDeployModeConditions.Cluster.class)
@RequiredArgsConstructor
public class ClusterScheduleMonitor {

    private static final String LOCK_NAME = "zestflow-admin-schedule-monitor-scan";

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedDelay = 15_000)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT10S"
    )
    public void scan() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.SCHEDULE_SCAN);
    }
}
