package com.zestflow.admin.schedule;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 单机部署租户清理 — {@code deploy-mode=standalone} 时启用，无 ShedLock。
 */
@Component
@Conditional(AdminDeployModeConditions.Standalone.class)
@RequiredArgsConstructor
public class StandaloneTenantCleanupMonitor {

    private final TenantCleanupService tenantCleanupService;

    @Scheduled(fixedRate = 300_000)
    public void cleanupTrialTenants() {
        tenantCleanupService.cleanupExpiredTrialTenants();
        tenantCleanupService.cleanupOrphanIpMappings();
    }
}
