package com.zestflow.admin.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.config.TenantContextHolder;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.entity.ScheduleLogPO;
import com.zestflow.admin.registry.RegistryLiveStore;
import com.zestflow.admin.registry.RegistryOnlineQuerySupport;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.repository.ScheduleLogMapper;
import com.zestflow.admin.repository.UserAppRoleMapper;
import com.zestflow.admin.service.MailService;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行 SLA 告警扫描 — 按 (tenantId, appCode) 评估规则并邮件通知模块负责人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSlaAlertService {

    private final AlertConfigService alertConfigService;
    private final UserAppRoleMapper userAppRoleMapper;
    private final AlertRecipientService recipientService;
    private final AlertCooldownService cooldownService;
    private final AlertHistoryService alertHistoryService;
    private final CollectorQueryAggregator collectorQueryAggregator;
    private final ExecutorRegistryMapper executorRegistryMapper;
    private final RegistryLiveStore liveStore;
    private final ScheduleLogMapper scheduleLogMapper;
    private final MailService mailService;

    public String scan() {
        List<TenantAppScope> scopes = userAppRoleMapper.selectDistinctTenantApps();
        if (scopes.isEmpty()) {
            return "scopes=0 alerts=0 emails=0";
        }

        int alertCount = 0;
        int emailCount = 0;
        Long previousTenant = TenantContextHolder.getTenantId();
        try {
            for (TenantAppScope scope : scopes) {
                if (scope.getTenantId() == null || scope.getAppCode() == null || scope.getAppCode().isBlank()) {
                    continue;
                }
                EffectiveAlertConfig config = alertConfigService.resolveEffective(scope.getTenantId());
                if (!config.isEnabled()) {
                    continue;
                }
                TenantContextHolder.setTenantId(scope.getTenantId());
                int[] sent = evaluateScope(scope.getTenantId(), scope.getAppCode(), config);
                alertCount += sent[0];
                emailCount += sent[1];
            }
        } finally {
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
        return "scopes=" + scopes.size() + " alerts=" + alertCount + " emails=" + emailCount;
    }

    private int[] evaluateScope(Long tenantId, String appCode, EffectiveAlertConfig config) {
        int alerts = 0;
        int emails = 0;

        long endMs = System.currentTimeMillis();
        long startMs = endMs - config.getWindowMinutes() * 60_000L;

        EventStats stats = collectorQueryAggregator.queryStats(
                EventStatsQuery.builder()
                        .tenantId(tenantId)
                        .appCode(appCode)
                        .startTime(startMs)
                        .endTime(endMs)
                        .build(),
                appCode);

        List<AlertCandidate> candidates = new ArrayList<>();
        int windowMinutes = config.getWindowMinutes();
        if (stats.getExecutionCount() >= config.getMinExecutions()
                && stats.getSuccessRate() < config.getSuccessRateThreshold()) {
            candidates.add(buildCandidate(AlertRule.LOW_SUCCESS_RATE, appCode, tenantId, windowMinutes,
                    config.getSubjectPrefix(),
                    "执行成功率低于阈值",
                    Map.of(
                            "执行次数", String.valueOf(stats.getExecutionCount()),
                            "成功率", String.format("%.2f%%", stats.getSuccessRate()),
                            "阈值", config.getSuccessRateThreshold() + "%"
                    )));
        }
        if (stats.getFailCount() >= config.getFailCountThreshold()) {
            candidates.add(buildCandidate(AlertRule.HIGH_FAIL_COUNT, appCode, tenantId, windowMinutes,
                    config.getSubjectPrefix(),
                    "失败执行次数过多",
                    Map.of(
                            "失败次数", String.valueOf(stats.getFailCount()),
                            "阈值", String.valueOf(config.getFailCountThreshold())
                    )));
        }
        if (stats.getExecutionCount() >= config.getMinExecutions()
                && stats.getP95CostMs() > config.getP95CostMsThreshold()) {
            candidates.add(buildCandidate(AlertRule.SLOW_P95, appCode, tenantId, windowMinutes,
                    config.getSubjectPrefix(),
                    "P95 耗时超过阈值",
                    Map.of(
                            "P95 耗时", stats.getP95CostMs() + " ms",
                            "阈值", config.getP95CostMsThreshold() + " ms"
                    )));
        }
        if (config.isAlertNoOnlineExecutor() && hasRegisteredExecutors(appCode)
                && RegistryOnlineQuerySupport.listLiveOnlineExecutors(
                        executorRegistryMapper, liveStore, appCode).isEmpty()) {
            candidates.add(buildCandidate(AlertRule.NO_ONLINE_EXECUTOR, appCode, tenantId, windowMinutes,
                    config.getSubjectPrefix(),
                    "模块无在线执行器",
                    Map.of("说明", "注册中心仍有执行器记录，但当前无存活实例")));
        }

        long scheduleFails = countScheduleFailures(tenantId, appCode, config.getWindowMinutes());
        if (scheduleFails >= config.getScheduleFailThreshold()) {
            candidates.add(buildCandidate(AlertRule.SCHEDULE_FAILURES, appCode, tenantId, windowMinutes,
                    config.getSubjectPrefix(),
                    "调度失败次数过多",
                    Map.of(
                            "失败次数", String.valueOf(scheduleFails),
                            "阈值", String.valueOf(config.getScheduleFailThreshold())
                    )));
        }

        List<String> recipients = recipientService.resolveRecipientEmails(tenantId, appCode);
        for (AlertCandidate candidate : candidates) {
            String key = AlertCooldownService.buildKey(tenantId, appCode, candidate.rule());
            if (!cooldownService.shouldSend(key, config.getCooldownMinutes())) {
                continue;
            }
            if (recipients.isEmpty()) {
                log.warn("SLA 告警无收件人 tenantId={} appCode={} rule={}", tenantId, appCode, candidate.rule());
                continue;
            }
            try {
                mailService.sendSlaAlertEmail(recipients, candidate.context());
                cooldownService.markSent(key);
                alertHistoryService.record(tenantId, appCode, candidate.rule(), candidate.context(), recipients);
                alerts++;
                emails += recipients.size();
            } catch (Exception e) {
                log.error("SLA 告警邮件发送失败 tenantId={} appCode={} rule={}",
                        tenantId, appCode, candidate.rule(), e);
            }
        }
        return new int[]{alerts, emails};
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
        return scheduleLogMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLogPO>()
                        .eq(ScheduleLogPO::getTenantId, tenantId)
                        .eq(ScheduleLogPO::getAppCode, appCode)
                        .eq(ScheduleLogPO::getStatus, 2)
                        .ge(ScheduleLogPO::getTriggeredAt, since));
    }

    private static AlertCandidate buildCandidate(AlertRule rule, String appCode, Long tenantId,
                                                  int windowMinutes, String subjectPrefix, String summary,
                                                  Map<String, String> metrics) {
        SlaAlertMailContext ctx = SlaAlertMailContext.builder()
                .tenantId(tenantId)
                .appCode(appCode)
                .ruleLabel(ruleLabel(rule))
                .summary(summary)
                .metrics(metrics)
                .windowMinutes(windowMinutes)
                .subjectPrefix(subjectPrefix)
                .build();
        return new AlertCandidate(rule, ctx);
    }

    private static String ruleLabel(AlertRule rule) {
        return switch (rule) {
            case LOW_SUCCESS_RATE -> "成功率过低";
            case HIGH_FAIL_COUNT -> "失败次数过多";
            case SLOW_P95 -> "P95 耗时过高";
            case NO_ONLINE_EXECUTOR -> "无在线执行器";
            case SCHEDULE_FAILURES -> "调度失败过多";
        };
    }

    private record AlertCandidate(AlertRule rule, SlaAlertMailContext context) {
    }
}
