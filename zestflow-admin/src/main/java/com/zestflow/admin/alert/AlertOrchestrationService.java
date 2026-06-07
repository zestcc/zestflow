package com.zestflow.admin.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryOnlineQuerySupport;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.admin.schedule.ScheduleChainProxyService;
import com.zestflow.admin.service.MailService;
import com.zestflow.common.alert.SlaAlertCandidate;
import com.zestflow.common.alert.SlaAlertEvaluationInput;
import com.zestflow.common.alert.SlaAlertEvaluator;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.SlaAlertMetricsReportDTO;
import com.zestflow.common.protocol.SlaAlertScopeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SLA 告警编排 — 配置/冷却/邮件在 Admin；Collector 上报本地 EventStats。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertOrchestrationService {

    private final AlertConfigService alertConfigService;
    private final UserAppRoleMapper userAppRoleMapper;
    private final AlertRecipientService recipientService;
    private final AlertCooldownService cooldownService;
    private final AlertHistoryService alertHistoryService;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final RegistryLiveStore liveStore;
    private final ScheduleLogMapper scheduleLogMapper;
    private final ScheduleChainProxyService scheduleChainProxyService;
    private final MailService mailService;

    public List<SlaAlertScopeDTO> listEnabledScopes() {
        List<SlaAlertScopeDTO> scopes = new ArrayList<>();
        for (TenantAppScope scope : userAppRoleMapper.selectDistinctTenantApps()) {
            if (scope.getTenantId() == null || scope.getAppCode() == null || scope.getAppCode().isBlank()) {
                continue;
            }
            EffectiveAlertConfig config = alertConfigService.resolveEffective(scope.getTenantId());
            if (!config.isEnabled()) {
                continue;
            }
            scopes.add(toScopeDto(scope, config));
        }
        return scopes;
    }

    public int[] processMetricsReport(SlaAlertMetricsReportDTO report) {
        if (report.getTenantId() == null || report.getAppCode() == null || report.getAppCode().isBlank()) {
            return new int[]{0, 0};
        }
        EffectiveAlertConfig config = alertConfigService.resolveEffective(report.getTenantId());
        if (!config.isEnabled()) {
            return new int[]{0, 0};
        }
        Long previousTenant = TenantContextHolder.getTenantId();
        try {
            TenantContextHolder.setTenantId(report.getTenantId());
            return evaluateAndDispatch(report.getTenantId(), report.getAppCode(), config, report.getEventStats());
        } finally {
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private int[] evaluateAndDispatch(Long tenantId, String appCode, EffectiveAlertConfig config, EventStats stats) {
        if (stats == null) {
            stats = EventStats.builder().build();
        }
        long scheduleFails = countScheduleFailures(tenantId, appCode, config.getWindowMinutes());
        SlaAlertEvaluationInput input = SlaAlertEvaluationInput.builder()
                .eventStats(stats)
                .windowMinutes(config.getWindowMinutes())
                .minExecutions(config.getMinExecutions())
                .successRateThreshold(config.getSuccessRateThreshold())
                .failCountThreshold(config.getFailCountThreshold())
                .p95CostMsThreshold(config.getP95CostMsThreshold())
                .scheduleFailThreshold(config.getScheduleFailThreshold())
                .alertNoOnlineExecutor(config.isAlertNoOnlineExecutor())
                .hasRegisteredExecutors(hasRegisteredExecutors(appCode))
                .hasOnlineExecutor(!RegistryOnlineQuerySupport.listLiveOnlineExecutors(
                        executorRegistryMapper, liveStore, appCode).isEmpty())
                .scheduleFailureCount(scheduleFails)
                .build();

        List<SlaAlertCandidate> candidates = SlaAlertEvaluator.evaluate(input);
        List<String> recipients = recipientService.resolveRecipientEmails(tenantId, appCode);
        int alerts = 0;
        int emails = 0;
        for (SlaAlertCandidate candidate : candidates) {
            String key = AlertCooldownService.buildKey(tenantId, appCode, toAdminRule(candidate.getRule()));
            if (!cooldownService.shouldSend(key, config.getCooldownMinutes())) {
                continue;
            }
            if (recipients.isEmpty()) {
                log.warn("SLA 告警无收件人 tenantId={} appCode={} rule={}", tenantId, appCode, candidate.getRule());
                continue;
            }
            SlaAlertMailContext ctx = SlaAlertMailContext.builder()
                    .tenantId(tenantId)
                    .appCode(appCode)
                    .ruleLabel(SlaAlertEvaluator.ruleLabel(candidate.getRule()))
                    .summary(candidate.getSummary())
                    .metrics(candidate.getMetrics())
                    .windowMinutes(config.getWindowMinutes())
                    .subjectPrefix(config.getSubjectPrefix())
                    .build();
            try {
                mailService.sendSlaAlertEmail(recipients, ctx);
                cooldownService.markSent(key);
                alertHistoryService.record(tenantId, appCode, toAdminRule(candidate.getRule()), ctx, recipients);
                alerts++;
                emails += recipients.size();
            } catch (Exception e) {
                log.error("SLA 告警邮件发送失败 tenantId={} appCode={} rule={}",
                        tenantId, appCode, candidate.getRule(), e);
            }
        }
        return new int[]{alerts, emails};
    }

    private static SlaAlertScopeDTO toScopeDto(TenantAppScope scope, EffectiveAlertConfig config) {
        return SlaAlertScopeDTO.builder()
                .tenantId(scope.getTenantId())
                .appCode(scope.getAppCode())
                .enabled(config.isEnabled())
                .cooldownMinutes(config.getCooldownMinutes())
                .windowMinutes(config.getWindowMinutes())
                .minExecutions(config.getMinExecutions())
                .successRateThreshold(config.getSuccessRateThreshold())
                .failCountThreshold(config.getFailCountThreshold())
                .p95CostMsThreshold(config.getP95CostMsThreshold())
                .scheduleFailThreshold(config.getScheduleFailThreshold())
                .alertNoOnlineExecutor(config.isAlertNoOnlineExecutor())
                .subjectPrefix(config.getSubjectPrefix())
                .build();
    }

    private boolean hasRegisteredExecutors(String appCode) {
        Long count = executorRegistryMapper.selectCount(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getAppCode, appCode)
                        .in(ExecutorRegistryPO::getStatus,
                                RegistryConstants.STATUS_ONLINE,
                                RegistryConstants.STATUS_ABNORMAL));
        return count != null && count > 0;
    }

    private long countScheduleFailures(Long tenantId, String appCode, int windowMinutes) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
        long platformFails = scheduleLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLogPO>()
                        .eq(ScheduleLogPO::getTenantId, tenantId)
                        .eq(ScheduleLogPO::getAppCode, appCode)
                        .eq(ScheduleLogPO::getStatus, 2)
                        .ge(ScheduleLogPO::getTriggeredAt, since));
        long chainFails = 0;
        try {
            chainFails = scheduleChainProxyService.countFailures(appCode, since);
        } catch (Exception e) {
            log.debug("链调度失败统计不可用 appCode={} error={}", appCode, e.getMessage());
        }
        return platformFails + chainFails;
    }

    private static AlertRule toAdminRule(com.zestflow.common.alert.AlertRule rule) {
        return AlertRule.valueOf(rule.name());
    }
}
