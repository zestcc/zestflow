package com.zestflow.admin.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.model.entity.CollectorRegistryPO;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import com.zestflow.admin.model.entity.UserAppRolePO;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.CollectorRegistryMapper;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.repository.TenantIpMappingMapper;
import com.zestflow.admin.repository.TenantMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试玩租户生命周期 — 滑动过期回收与级联删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLifecycleService {

    private static final long SYSTEM_TEMPLATE_TENANT_ID = 1L;

    private final TenantMapper tenantMapper;
    private final TenantIpMappingMapper tenantIpMappingMapper;
    private final PlaygroundSceneMapper playgroundSceneMapper;
    private final PlaygroundRecordMapper playgroundRecordMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleLogMapper scheduleLogMapper;
    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;
    private final RoleMapper roleMapper;
    private final UserAppRoleMapper userAppRoleMapper;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final CollectorRegistryMapper collectorRegistryMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean purgeTrialTenant(long tenantId) {
        if (tenantId <= SYSTEM_TEMPLATE_TENANT_ID) {
            return false;
        }
        TenantPO tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !TenantTypes.TRIAL.equals(tenant.getTenantType())) {
            return false;
        }

        Long previous = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(tenantId);
            playgroundRecordMapper.delete(
                    new LambdaQueryWrapper<PlaygroundRecordPO>().eq(PlaygroundRecordPO::getTenantId, tenantId));
            playgroundSceneMapper.delete(
                    new LambdaQueryWrapper<PlaygroundScenePO>().eq(PlaygroundScenePO::getTenantId, tenantId));
            scheduleLogMapper.delete(
                    new LambdaQueryWrapper<ScheduleLogPO>().eq(ScheduleLogPO::getTenantId, tenantId));
            scheduleMapper.delete(
                    new LambdaQueryWrapper<SchedulePO>().eq(SchedulePO::getTenantId, tenantId));
            dictDataMapper.delete(
                    new LambdaQueryWrapper<DictDataPO>().eq(DictDataPO::getTenantId, tenantId));
            dictTypeMapper.delete(
                    new LambdaQueryWrapper<DictTypePO>().eq(DictTypePO::getTenantId, tenantId));
            userAppRoleMapper.delete(
                    new LambdaQueryWrapper<UserAppRolePO>().eq(UserAppRolePO::getTenantId, tenantId));
            roleMapper.delete(
                    new LambdaQueryWrapper<RolePO>().eq(RolePO::getTenantId, tenantId));
            executorRegistryMapper.delete(
                    new LambdaQueryWrapper<ExecutorRegistryPO>().eq(ExecutorRegistryPO::getTenantId, tenantId));
            collectorRegistryMapper.delete(
                    new LambdaQueryWrapper<CollectorRegistryPO>().eq(CollectorRegistryPO::getTenantId, tenantId));
        } finally {
            if (previous != null) {
                TenantContextHolder.setTenantId(previous);
            } else {
                TenantContextHolder.clear();
            }
        }

        tenantIpMappingMapper.delete(
                new LambdaQueryWrapper<TenantIpMappingPO>().eq(TenantIpMappingPO::getTenantId, tenantId));
        tenantMapper.deleteById(tenantId);
        log.info("试玩租户已回收 tenantId={} code={}", tenantId, tenant.getCode());
        return true;
    }
}
