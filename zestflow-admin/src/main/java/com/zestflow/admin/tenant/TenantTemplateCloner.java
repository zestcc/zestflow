package com.zestflow.admin.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.config.TenantModeConfig;
import com.zestflow.admin.model.entity.DictDataPO;
import com.zestflow.admin.model.entity.DictTypePO;
import com.zestflow.admin.model.entity.RolePO;
import com.zestflow.admin.model.entity.SchedulePO;
import com.zestflow.admin.model.entity.SysConfigPO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.repository.DictDataMapper;
import com.zestflow.admin.repository.DictTypeMapper;
import com.zestflow.admin.repository.RoleMapper;
import com.zestflow.admin.repository.ScheduleMapper;
import com.zestflow.admin.repository.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * 从母版租户克隆开户模板数据（角色、字典、演示场景、调度等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantTemplateCloner {

    public static final String DEMO_APP_CODE = "demo-app";

    private final RoleMapper roleMapper;
    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;
    private final PlaygroundSceneMapper playgroundSceneMapper;
    private final ScheduleMapper scheduleMapper;
    private final SysConfigMapper sysConfigMapper;
    private final TenantModeConfig tenantModeConfig;

    /**
     * 全盘克隆母版租户下所有可拷贝业务数据。
     */
    public TenantCloneSummary cloneFromTemplate(long targetTenantId, long templateTenantId) {
        if (targetTenantId == templateTenantId) {
            return TenantCloneSummary.empty();
        }

        int roles = runAsTenant(targetTenantId, () -> cloneRoles(targetTenantId, templateTenantId));
        int dictTypes = runAsTenant(targetTenantId, () -> cloneDictTypes(targetTenantId, templateTenantId));
        int dictData = runAsTenant(targetTenantId, () -> cloneDictData(targetTenantId, templateTenantId));
        int scenes = runAsTenant(targetTenantId, () -> clonePlaygroundScenes(targetTenantId, templateTenantId));
        int schedules = runAsTenant(targetTenantId, () -> cloneSchedules(targetTenantId, templateTenantId));
        int sysConfigs = runAsTenant(targetTenantId, () -> cloneSysConfigs(targetTenantId, templateTenantId));

        TenantCloneSummary summary = TenantCloneSummary.builder()
                .roles(roles)
                .dictTypes(dictTypes)
                .dictData(dictData)
                .playgroundScenes(scenes)
                .schedules(schedules)
                .sysConfigs(sysConfigs)
                .build();

        log.info("母版克隆完成 targetTenantId={} templateTenantId={} summary={}",
                targetTenantId, templateTenantId, summary.totalItems());
        return summary;
    }

    /** @deprecated 使用 {@link #cloneFromTemplate(long, long)} */
    @Deprecated
    public int clonePlaygroundScenes(long targetTenantId, long templateTenantId) {
        return runAsTenant(targetTenantId, () -> clonePlaygroundScenesInternal(targetTenantId, templateTenantId));
    }

    public long resolveTemplateTenantId(Long override) {
        if (override != null && override > 0) {
            return override;
        }
        return tenantModeConfig.getTemplateTenantId();
    }

    private int cloneRoles(long targetTenantId, long templateTenantId) {
        List<RolePO> templates = roleMapper.selectList(
                new LambdaQueryWrapper<RolePO>().eq(RolePO::getTenantId, templateTenantId));
        if (templates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (RolePO src : templates) {
            RolePO copy = new RolePO();
            copy.setCode(src.getCode());
            copy.setName(src.getName());
            copy.setDescription(src.getDescription());
            copy.setTenantId(targetTenantId);
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            roleMapper.insert(copy);
        }
        return templates.size();
    }

    private int cloneDictTypes(long targetTenantId, long templateTenantId) {
        List<DictTypePO> templates = dictTypeMapper.selectList(
                new LambdaQueryWrapper<DictTypePO>().eq(DictTypePO::getTenantId, templateTenantId));
        if (templates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DictTypePO src : templates) {
            DictTypePO copy = new DictTypePO();
            copy.setCode(src.getCode());
            copy.setName(src.getName());
            copy.setDescription(src.getDescription());
            copy.setStatus(src.getStatus());
            copy.setSort(src.getSort());
            copy.setAppCode(src.getAppCode());
            copy.setTenantId(targetTenantId);
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            dictTypeMapper.insert(copy);
        }
        return templates.size();
    }

    private int cloneDictData(long targetTenantId, long templateTenantId) {
        List<DictDataPO> templates = dictDataMapper.selectList(
                new LambdaQueryWrapper<DictDataPO>().eq(DictDataPO::getTenantId, templateTenantId));
        if (templates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, Long> idMap = new HashMap<>();
        for (DictDataPO src : templates) {
            DictDataPO copy = new DictDataPO();
            copy.setTypeCode(src.getTypeCode());
            copy.setParentTypeCode(src.getParentTypeCode());
            copy.setParentValue(src.getParentValue());
            copy.setLabel(src.getLabel());
            copy.setValue(src.getValue());
            copy.setSort(src.getSort());
            copy.setStatus(src.getStatus());
            copy.setTagType(src.getTagType());
            copy.setDefaultFlag(src.getDefaultFlag());
            copy.setRemark(src.getRemark());
            copy.setExtra(src.getExtra());
            copy.setAppCode(src.getAppCode());
            copy.setTenantId(targetTenantId);
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            dictDataMapper.insert(copy);
            idMap.put(src.getId(), copy.getId());
        }
        for (DictDataPO src : templates) {
            if (src.getParentId() == null) {
                continue;
            }
            Long newId = idMap.get(src.getId());
            Long newParentId = idMap.get(src.getParentId());
            if (newId == null || newParentId == null) {
                continue;
            }
            DictDataPO patch = new DictDataPO();
            patch.setId(newId);
            patch.setParentId(newParentId);
            dictDataMapper.updateById(patch);
        }
        return templates.size();
    }

    private int cloneSysConfigs(long targetTenantId, long templateTenantId) {
        List<SysConfigPO> templates = sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfigPO>().eq(SysConfigPO::getTenantId, templateTenantId));
        if (templates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysConfigPO src : templates) {
            SysConfigPO copy = new SysConfigPO();
            copy.setConfigKey(src.getConfigKey());
            copy.setConfigName(src.getConfigName());
            copy.setConfigValue(src.getConfigValue());
            copy.setValueType(src.getValueType());
            copy.setCategory(src.getCategory());
            copy.setStatus(src.getStatus());
            copy.setSort(src.getSort());
            copy.setRemark(src.getRemark());
            copy.setTenantId(targetTenantId);
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            sysConfigMapper.insert(copy);
        }
        return templates.size();
    }

    private int clonePlaygroundScenesInternal(long targetTenantId, long templateTenantId) {
        List<PlaygroundScenePO> templates = playgroundSceneMapper.selectList(
                new LambdaQueryWrapper<PlaygroundScenePO>()
                        .eq(PlaygroundScenePO::getTenantId, templateTenantId));

        if (templates.isEmpty()) {
            log.warn("母版租户无演示场景，跳过克隆 templateTenantId={}", templateTenantId);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        for (PlaygroundScenePO src : templates) {
            PlaygroundScenePO copy = new PlaygroundScenePO();
            copy.setSceneCode(src.getSceneCode());
            copy.setName(src.getName());
            copy.setDescription(src.getDescription());
            copy.setRequestPath(src.getRequestPath());
            copy.setRequestMethod(src.getRequestMethod());
            copy.setRequestHeaders(src.getRequestHeaders());
            copy.setBodyType(src.getBodyType());
            copy.setRequestBody(src.getRequestBody());
            copy.setResponseExample(src.getResponseExample());
            copy.setChainCode(src.getChainCode());
            copy.setRateLimit(src.getRateLimit());
            copy.setTenantId(targetTenantId);
            copy.setAppCode(src.getAppCode());
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            playgroundSceneMapper.insert(copy);
        }
        return templates.size();
    }

    private int cloneSchedules(long targetTenantId, long templateTenantId) {
        List<SchedulePO> templates = scheduleMapper.selectList(
                new LambdaQueryWrapper<SchedulePO>().eq(SchedulePO::getTenantId, templateTenantId));
        if (templates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SchedulePO src : templates) {
            SchedulePO copy = new SchedulePO();
            copy.setChainId(src.getChainId());
            copy.setChainCode(src.getChainCode());
            copy.setChainName(src.getChainName());
            copy.setCron(src.getCron());
            copy.setRouteStrategy(src.getRouteStrategy());
            copy.setParams(src.getParams());
            copy.setStatus(src.getStatus());
            copy.setRemark(src.getRemark());
            copy.setAppCode(src.getAppCode());
            copy.setTenantId(targetTenantId);
            copy.setCreatedBy("tenant-provision");
            copy.setUpdatedBy("tenant-provision");
            copy.setCreatedAt(now);
            copy.setUpdatedAt(now);
            scheduleMapper.insert(copy);
        }
        return templates.size();
    }

    private int runAsTenant(long targetTenantId, IntSupplier action) {
        Long previousTenant = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(targetTenantId);
            return action.getAsInt();
        } finally {
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
    }
}

