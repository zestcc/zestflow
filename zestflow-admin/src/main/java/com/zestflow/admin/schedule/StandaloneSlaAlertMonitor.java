package com.zestflow.admin.schedule;

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
public class StandaloneSlaAlertMonitor {

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRateString = "${zestflow.alert.scan-interval-ms:300000}")
    public void scan() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.EXECUTION_SLA_ALERT);
    }
}
