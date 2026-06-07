package com.zestflow.admin.schedule.platform;

import com.zestflow.admin.runtime.ChainSyncCacheEvictor;
import com.zestflow.admin.runtime.ExecutorChainDriftMonitor;
import com.zestflow.admin.schedule.TenantCleanupService;
import com.zestflow.admin.config.OfflineMonitorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PlatformJobConfiguration {

    private final PlatformJobHandlerRegistry registry;
    private final OfflineMonitorService offlineMonitorService;
    private final TenantCleanupService tenantCleanupService;
    private final ChainSyncCacheEvictor chainSyncCacheEvictor;
    private final ExecutorChainDriftMonitor chainDriftMonitor;

    @PostConstruct
    void registerHandlers() {
        registry.register(PlatformJobKeys.OFFLINE_CLEANUP, () -> {
            offlineMonitorService.cleanupStaleAbnormal();
            return null;
        });
        registry.register(PlatformJobKeys.TENANT_CLEANUP, () -> {
            tenantCleanupService.cleanupExpiredTrialTenants();
            tenantCleanupService.cleanupOrphanIpMappings();
            return null;
        });
        registry.register(PlatformJobKeys.CHAIN_SYNC_CACHE_EVICT, () -> {
            chainSyncCacheEvictor.evictStale();
            return null;
        });
        registry.register(PlatformJobKeys.CHAIN_DRIFT_RECONCILE, () -> {
            chainDriftMonitor.reconcileActiveChains();
            return null;
        });
    }
}
