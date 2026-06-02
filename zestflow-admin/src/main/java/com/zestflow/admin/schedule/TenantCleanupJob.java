package com.zestflow.admin.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 租户清理任务 — 定期清理不活跃的 IP 租户映射
 * <p>
 * 扫描超过 1 小时无活动的 tenant_ip_mapping 记录并删除，同时更新对应租户的 lastActiveAt。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCleanupJob {

    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantMapper tenantMapper;

    /**
     * 每 5 分钟扫描一次，清理 1 小时无活动的 IP 租户映射
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanupInactiveIpMappings() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        int deleted = tenantIpMappingMapper.delete(
                new LambdaQueryWrapper<TenantIpMappingPO>()
                        .lt(TenantIpMappingPO::getLastActiveAt, threshold)
        );
        if (deleted > 0) {
            log.info("租户 IP 映射清理：删除了 {} 条超过 1 小时无活动的记录", deleted);
        }
    }

    /**
     * 每 5 分钟扫描一次，更新不活跃租户的 lastActiveAt 为空闲状态标记
     */
    @Scheduled(fixedRate = 300_000)
    public void updateInactiveTenants() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        TenantPO updateEntity = new TenantPO();
        updateEntity.setStatus(0);
        int updated = tenantMapper.update(
                updateEntity,
                new LambdaQueryWrapper<TenantPO>()
                        .lt(TenantPO::getLastActiveAt, threshold)
                        .eq(TenantPO::getStatus, 1)
        );
        if (updated > 0) {
            log.info("租户活跃状态检查：{} 个租户超过 1 小时无活动，已标记为不活跃", updated);
        }
    }
}
