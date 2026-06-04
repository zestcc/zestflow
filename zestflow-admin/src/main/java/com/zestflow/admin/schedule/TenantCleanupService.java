package com.zestflow.admin.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.tenant.TenantLifecycleService;
import com.zestflow.admin.tenant.TenantTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户清理 — 试玩租户滑动过期回收（统一 Provision 模型）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCleanupService {

    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantMapper tenantMapper;
    private final TenantModeConfig tenantModeConfig;
    private final TenantLifecycleService tenantLifecycleService;

    public void cleanupExpiredTrialTenants() {
        if (!tenantModeConfig.isTrialLifecycleEnabled()) {
            return;
        }
        LocalDateTime slidingThreshold = LocalDateTime.now()
                .minusMinutes(tenantModeConfig.getIpTenantTimeoutMinutes());
        LocalDateTime now = LocalDateTime.now();

        List<TenantPO> expired = tenantMapper.selectList(
                new LambdaQueryWrapper<TenantPO>()
                        .eq(TenantPO::getTenantType, TenantTypes.TRIAL)
                        .and(w -> w
                                .lt(TenantPO::getLastActiveAt, slidingThreshold)
                                .or(o -> o.isNotNull(TenantPO::getExpiresAt).lt(TenantPO::getExpiresAt, now))));

        int purged = 0;
        for (TenantPO tenant : expired) {
            if (tenantLifecycleService.purgeTrialTenant(tenant.getId())) {
                purged++;
            }
        }
        if (purged > 0) {
            log.info("试玩租户回收：已删除 {} 个超过 {} 分钟无活动或已过期的 trial 租户",
                    purged, tenantModeConfig.getIpTenantTimeoutMinutes());
        }
    }

    /** 清理孤儿 IP 映射（租户已不存在） */
    public void cleanupOrphanIpMappings() {
        List<TenantIpMappingPO> mappings = tenantIpMappingMapper.selectList(null);
        int removed = 0;
        for (TenantIpMappingPO mapping : mappings) {
            TenantPO tenant = tenantMapper.selectById(mapping.getTenantId());
            if (tenant == null) {
                tenantIpMappingMapper.deleteById(mapping.getId());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("孤儿 IP 映射清理：删除 {} 条", removed);
        }
    }

    /** @deprecated 使用 {@link #cleanupExpiredTrialTenants()} */
    @Deprecated
    public void cleanupInactiveIpMappings() {
        cleanupOrphanIpMappings();
    }

    /** @deprecated 使用 {@link #cleanupExpiredTrialTenants()} */
    @Deprecated
    public void updateInactiveTenants() {
        cleanupExpiredTrialTenants();
    }
}
