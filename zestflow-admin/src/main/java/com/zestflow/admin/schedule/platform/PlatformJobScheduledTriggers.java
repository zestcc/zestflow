package com.zestflow.admin.schedule.platform;

import com.zestflow.common.constant.RegistryConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlatformJobScheduledTriggers {

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedRate = RegistryConstants.HEARTBEAT_DB_FLUSH_INTERVAL_MS)
    public void heartbeatDbFlush() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.HEARTBEAT_DB_FLUSH);
    }
}
