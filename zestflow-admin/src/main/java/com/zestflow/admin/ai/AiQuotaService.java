package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiTenantConfigPO;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiTenantConfigMapper;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiQuotaService {

    private final AiTenantConfigMapper configMapper;
    private final AiCopilotMessageMapper messageMapper;
    private final AiProperties aiProperties;

    public void ensureWithinQuota(Long tenantId) {
        Integer quota = resolveMonthlyQuota(tenantId);
        if (quota == null || quota <= 0) {
            return;
        }
        long used = sumMonthlyTokenEstimate(tenantId);
        if (used >= quota) {
            throw new BizException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
    }

    public Integer resolveMonthlyQuota(Long tenantId) {
        AiTenantConfigPO po = configMapper.selectOne(new LambdaQueryWrapper<AiTenantConfigPO>()
                .eq(AiTenantConfigPO::getTenantId, tenantId)
                .eq(AiTenantConfigPO::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (po != null && po.getMonthlyTokenQuota() != null) {
            return po.getMonthlyTokenQuota();
        }
        int globalDefault = aiProperties.getDefaultMonthlyTokenQuota();
        return globalDefault > 0 ? globalDefault : null;
    }

    public long sumMonthlyTokenEstimate(Long tenantId) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return messageMapper.selectList(new LambdaQueryWrapper<AiCopilotMessagePO>()
                        .eq(AiCopilotMessagePO::getTenantId, tenantId)
                        .ge(AiCopilotMessagePO::getCreatedAt, monthStart))
                .stream()
                .map(AiCopilotMessagePO::getTokenEstimate)
                .filter(v -> v != null && v > 0)
                .mapToLong(Integer::longValue)
                .sum();
    }
}
