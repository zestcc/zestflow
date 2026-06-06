package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import com.zestflow.admin.schedule.platform.PlatformJobKeys;
import com.zestflow.admin.schedule.platform.PlatformJobRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneOfflineMonitor {

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRate = 30_000)
    public void checkOffline() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.OFFLINE_CHECK);
    }

    @Scheduled(fixedRate = 1_800_000)
    public void cleanupStaleAbnormal() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.OFFLINE_CLEANUP);
    }
}
