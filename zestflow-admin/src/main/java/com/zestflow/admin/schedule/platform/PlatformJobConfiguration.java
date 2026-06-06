package com.zestflow.admin.schedule.platform;

import com.zestflow.admin.config.OfflineMonitorService;
import com.zestflow.admin.alert.ExecutionSlaAlertService;
import com.zestflow.admin.registry.RegistryHeartbeatDbFlushMonitor;
import com.zestflow.admin.runtime.ChainSyncCacheEvictor;
import com.zestflow.admin.runtime.ExecutorChainDriftMonitor;
import com.zestflow.admin.schedule.ScheduleScanService;
import com.zestflow.admin.schedule.TenantCleanupService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PlatformJobConfiguration {

    private final PlatformJobHandlerRegistry registry;
    private final ScheduleScanService scheduleScanService;
    private final OfflineMonitorService offlineMonitorService;
    private final TenantCleanupService tenantCleanupService;
    private final ChainSyncCacheEvictor chainSyncCacheEvictor;
    private final RegistryHeartbeatDbFlushMonitor heartbeatDbFlushMonitor;
    private final ExecutorChainDriftMonitor chainDriftMonitor;
    private final ExecutionSlaAlertService executionSlaAlertService;

    @PostConstruct
    void registerHandlers() {
        registry.register(PlatformJobKeys.SCHEDULE_SCAN, () -> {
            scheduleScanService.scanAndTriggerDueSchedules();
            return null;
        });
        registry.register(PlatformJobKeys.OFFLINE_CHECK, () -> {
            offlineMonitorService.checkOffline();
            return null;
        });
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
        registry.register(PlatformJobKeys.HEARTBEAT_DB_FLUSH, () -> {
            heartbeatDbFlushMonitor.flushLastHeartbeatToDb();
            return null;
        });
        registry.register(PlatformJobKeys.CHAIN_DRIFT_RECONCILE, () -> {
            chainDriftMonitor.reconcileActiveChains();
            return null;
        });
        registry.register(PlatformJobKeys.EXECUTION_SLA_ALERT, () ->
                executionSlaAlertService.scan());
    }
}
