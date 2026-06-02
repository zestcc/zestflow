package com.zestflow.admin.config;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单机部署离线检测 — {@code deploy-mode=standalone} 时启用，无 ShedLock。
 */
@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneOfflineMonitor {

    private final OfflineMonitorService offlineMonitorService;

    @Scheduled(fixedRate = 30_000)
    public void checkOffline() {
        offlineMonitorService.checkOffline();
    }

    @Scheduled(fixedRate = 1_800_000)
    public void cleanupStaleAbnormal() {
        offlineMonitorService.cleanupStaleAbnormal();
    }
}
