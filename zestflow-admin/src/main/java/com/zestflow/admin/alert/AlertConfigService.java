package com.zestflow.admin.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.AlertPlatformConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import com.zestflow.admin.model.dto.AlertConfigSaveDTO;
import com.zestflow.admin.model.entity.AlertTenantConfigPO;
import com.zestflow.admin.model.vo.AlertConfigVO;
import com.zestflow.admin.repository.AlertTenantConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AlertConfigService {

    private final AlertPlatformConfig alertPlatformConfig;
    private final AlertTenantConfigMapper configMapper;

    public EffectiveAlertConfig resolveEffective(Long tenantId) {
        AlertTenantConfigPO override = findByTenantId(tenantId);
        return merge(override);
    }

    public AlertConfigVO getConfig(Long tenantId) {
        AlertTenantConfigPO override = findByTenantId(tenantId);
        EffectiveAlertConfig effective = merge(override);
        return AlertConfigVO.builder()
                .enabled(effective.isEnabled())
                .cooldownMinutes(effective.getCooldownMinutes())
                .windowMinutes(effective.getWindowMinutes())
                .minExecutions(effective.getMinExecutions())
                .successRateThreshold(effective.getSuccessRateThreshold())
                .failCountThreshold(effective.getFailCountThreshold())
                .p95CostMsThreshold(effective.getP95CostMsThreshold())
                .scheduleFailThreshold(effective.getScheduleFailThreshold())
                .alertNoOnlineExecutor(effective.isAlertNoOnlineExecutor())
                .subjectPrefix(effective.getSubjectPrefix())
                .scanIntervalMs(alertPlatformConfig.getScanIntervalMs())
                .defaults(fromYml())
                .tenantOverride(override != null)
                .build();
    }

    public AlertConfigVO saveConfig(Long tenantId, AlertConfigSaveDTO dto) {
        validate(dto);
        AlertTenantConfigPO po = findByTenantId(tenantId);
        if (po == null) {
            po = new AlertTenantConfigPO();
            po.setTenantId(tenantId);
        }
        if (dto.getEnabled() != null) {
            po.setEnabled(dto.getEnabled() ? 1 : 0);
        }
        if (dto.getCooldownMinutes() != null) {
            po.setCooldownMinutes(dto.getCooldownMinutes());
        }
        if (dto.getWindowMinutes() != null) {
            po.setWindowMinutes(dto.getWindowMinutes());
        }
        if (dto.getMinExecutions() != null) {
            po.setMinExecutions(dto.getMinExecutions());
        }
        if (dto.getSuccessRateThreshold() != null) {
            po.setSuccessRateThreshold(BigDecimal.valueOf(dto.getSuccessRateThreshold()));
        }
        if (dto.getFailCountThreshold() != null) {
            po.setFailCountThreshold(dto.getFailCountThreshold());
        }
        if (dto.getP95CostMsThreshold() != null) {
            po.setP95CostMsThreshold(dto.getP95CostMsThreshold());
        }
        if (dto.getScheduleFailThreshold() != null) {
            po.setScheduleFailThreshold(dto.getScheduleFailThreshold());
        }
        if (dto.getAlertNoOnlineExecutor() != null) {
            po.setAlertNoOnlineExecutor(dto.getAlertNoOnlineExecutor() ? 1 : 0);
        }
        if (dto.getSubjectPrefix() != null) {
            po.setSubjectPrefix(StringUtils.hasText(dto.getSubjectPrefix())
                    ? dto.getSubjectPrefix().trim() : null);
        }
        if (po.getId() == null) {
            configMapper.insert(po);
        } else {
            configMapper.updateById(po);
        }
        return getConfig(tenantId);
    }

    public AlertConfigVO resetConfig(Long tenantId) {
        AlertTenantConfigPO existing = findByTenantId(tenantId);
        if (existing != null) {
            configMapper.deleteById(existing.getId());
        }
        return getConfig(tenantId);
    }

    private void validate(AlertConfigSaveDTO dto) {
        if (dto.getCooldownMinutes() != null && dto.getCooldownMinutes() < 1) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getWindowMinutes() != null && dto.getWindowMinutes() < 1) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getMinExecutions() != null && dto.getMinExecutions() < 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
        if (dto.getSuccessRateThreshold() != null
                && (dto.getSuccessRateThreshold() < 0 || dto.getSuccessRateThreshold() > 100)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private AlertTenantConfigPO findByTenantId(Long tenantId) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<AlertTenantConfigPO>()
                        .eq(AlertTenantConfigPO::getTenantId, tenantId));
    }

    private EffectiveAlertConfig merge(AlertTenantConfigPO override) {
        return EffectiveAlertConfig.builder()
                .enabled(override != null && override.getEnabled() != null
                        ? override.getEnabled() == 1 : alertPlatformConfig.isEnabled())
                .cooldownMinutes(orInt(override != null ? override.getCooldownMinutes() : null,
                        alertPlatformConfig.getCooldownMinutes()))
                .windowMinutes(orInt(override != null ? override.getWindowMinutes() : null,
                        alertPlatformConfig.getWindowMinutes()))
                .minExecutions(orInt(override != null ? override.getMinExecutions() : null,
                        alertPlatformConfig.getMinExecutions()))
                .successRateThreshold(override != null && override.getSuccessRateThreshold() != null
                        ? override.getSuccessRateThreshold().doubleValue()
                        : alertPlatformConfig.getSuccessRateThreshold())
                .failCountThreshold(orInt(override != null ? override.getFailCountThreshold() : null,
                        alertPlatformConfig.getFailCountThreshold()))
                .p95CostMsThreshold(override != null && override.getP95CostMsThreshold() != null
                        ? override.getP95CostMsThreshold()
                        : alertPlatformConfig.getP95CostMsThreshold())
                .scheduleFailThreshold(orInt(override != null ? override.getScheduleFailThreshold() : null,
                        alertPlatformConfig.getScheduleFailThreshold()))
                .alertNoOnlineExecutor(override != null && override.getAlertNoOnlineExecutor() != null
                        ? override.getAlertNoOnlineExecutor() == 1
                        : alertPlatformConfig.isAlertNoOnlineExecutor())
                .subjectPrefix(override != null && StringUtils.hasText(override.getSubjectPrefix())
                        ? override.getSubjectPrefix()
                        : alertPlatformConfig.getSubjectPrefix())
                .build();
    }

    private AlertConfigVO fromYml() {
        return AlertConfigVO.builder()
                .enabled(alertPlatformConfig.isEnabled())
                .cooldownMinutes(alertPlatformConfig.getCooldownMinutes())
                .windowMinutes(alertPlatformConfig.getWindowMinutes())
                .minExecutions(alertPlatformConfig.getMinExecutions())
                .successRateThreshold(alertPlatformConfig.getSuccessRateThreshold())
                .failCountThreshold(alertPlatformConfig.getFailCountThreshold())
                .p95CostMsThreshold(alertPlatformConfig.getP95CostMsThreshold())
                .scheduleFailThreshold(alertPlatformConfig.getScheduleFailThreshold())
                .alertNoOnlineExecutor(alertPlatformConfig.isAlertNoOnlineExecutor())
                .subjectPrefix(alertPlatformConfig.getSubjectPrefix())
                .scanIntervalMs(alertPlatformConfig.getScanIntervalMs())
                .build();
    }

    private static int orInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}
