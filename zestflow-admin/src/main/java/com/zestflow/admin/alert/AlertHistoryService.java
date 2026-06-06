package com.zestflow.admin.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.model.entity.AlertHistoryPO;
import com.zestflow.admin.model.vo.AlertHistoryVO;
import com.zestflow.admin.repository.AlertHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertHistoryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AlertHistoryMapper alertHistoryMapper;

    @Value("${zestflow.mail.enabled:false}")
    private boolean mailEnabled;

    public void record(Long tenantId, String appCode, AlertRule rule, SlaAlertMailContext context,
                       List<String> recipients) {
        AlertHistoryPO po = new AlertHistoryPO();
        po.setTenantId(tenantId);
        po.setAppCode(appCode);
        po.setRuleCode(rule.name());
        po.setRuleLabel(context.getRuleLabel());
        po.setSummary(context.getSummary());
        po.setMetricsJson(writeMetrics(context.getMetrics()));
        po.setRecipientCount(recipients != null ? recipients.size() : 0);
        po.setRecipients(maskRecipients(recipients));
        po.setMailSent(mailEnabled ? 1 : 0);
        po.setSentAt(LocalDateTime.now());
        alertHistoryMapper.insert(po);
    }

    public IPage<AlertHistoryVO> list(Long tenantId, String appCode, String ruleCode,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      int page, int size, Set<String> allowedAppCodes) {
        LambdaQueryWrapper<AlertHistoryPO> wrapper = new LambdaQueryWrapper<AlertHistoryPO>()
                .eq(AlertHistoryPO::getTenantId, tenantId)
                .eq(StringUtils.hasText(appCode), AlertHistoryPO::getAppCode, appCode)
                .eq(StringUtils.hasText(ruleCode), AlertHistoryPO::getRuleCode, ruleCode)
                .ge(startTime != null, AlertHistoryPO::getSentAt, startTime)
                .le(endTime != null, AlertHistoryPO::getSentAt, endTime)
                .orderByDesc(AlertHistoryPO::getSentAt);
        if (allowedAppCodes != null && !allowedAppCodes.isEmpty()) {
            wrapper.in(AlertHistoryPO::getAppCode, allowedAppCodes);
        }
        Page<AlertHistoryPO> pageReq = new Page<>(page, size);
        IPage<AlertHistoryPO> result = alertHistoryMapper.selectPage(pageReq, wrapper);
        return result.convert(this::toVo);
    }

    private AlertHistoryVO toVo(AlertHistoryPO po) {
        return AlertHistoryVO.builder()
                .id(po.getId())
                .appCode(po.getAppCode())
                .ruleCode(po.getRuleCode())
                .ruleLabel(po.getRuleLabel())
                .summary(po.getSummary())
                .metrics(readMetrics(po.getMetricsJson()))
                .recipientCount(po.getRecipientCount())
                .recipients(po.getRecipients())
                .mailSent(po.getMailSent() != null && po.getMailSent() == 1)
                .sentAt(po.getSentAt())
                .build();
    }

    private String writeMetrics(Map<String, String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(metrics);
        } catch (Exception e) {
            log.warn("告警指标序列化失败", e);
            return null;
        }
    }

    private Map<String, String> readMetrics(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    static String maskRecipients(List<String> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return null;
        }
        return recipients.stream()
                .map(AlertHistoryService::maskEmail)
                .collect(Collectors.joining(", "));
    }

    static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
