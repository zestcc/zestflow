package com.zestflow.admin.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 租户清理逻辑 — 供单机 / 集群调度入口复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCleanupService {

    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final TenantMapper tenantMapper;

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
