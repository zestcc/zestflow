package com.zestflow.common.alert;

import com.zestflow.common.protocol.EventStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SLA 规则纯函数评估 — 不含邮件/DB，供 Admin 与 Collector 共用。
 */
public final class SlaAlertEvaluator {

    private SlaAlertEvaluator() {}

    public static List<SlaAlertCandidate> evaluate(SlaAlertEvaluationInput input) {
        List<SlaAlertCandidate> candidates = new ArrayList<>();
        EventStats stats = input.getEventStats();
        int windowMinutes = input.getWindowMinutes();

        if (stats.getExecutionCount() >= input.getMinExecutions()
                && stats.getSuccessRate() < input.getSuccessRateThreshold()) {
            candidates.add(candidate(AlertRule.LOW_SUCCESS_RATE, windowMinutes,
                    "执行成功率低于阈值",
                    Map.of(
                            "执行次数", String.valueOf(stats.getExecutionCount()),
                            "成功率", String.format("%.2f%%", stats.getSuccessRate()),
                            "阈值", input.getSuccessRateThreshold() + "%"
                    )));
        }
        if (stats.getFailCount() >= input.getFailCountThreshold()) {
            candidates.add(candidate(AlertRule.HIGH_FAIL_COUNT, windowMinutes,
                    "失败执行次数过多",
                    Map.of(
                            "失败次数", String.valueOf(stats.getFailCount()),
                            "阈值", String.valueOf(input.getFailCountThreshold())
                    )));
        }
        if (stats.getExecutionCount() >= input.getMinExecutions()
                && stats.getP95CostMs() > input.getP95CostMsThreshold()) {
            candidates.add(candidate(AlertRule.SLOW_P95, windowMinutes,
                    "P95 耗时超过阈值",
                    Map.of(
                            "P95 耗时", stats.getP95CostMs() + " ms",
                            "阈值", input.getP95CostMsThreshold() + " ms"
                    )));
        }
        if (input.isAlertNoOnlineExecutor() && input.isHasRegisteredExecutors()
                && !input.isHasOnlineExecutor()) {
            candidates.add(candidate(AlertRule.NO_ONLINE_EXECUTOR, windowMinutes,
                    "模块无在线执行器",
                    Map.of("说明", "注册中心仍有执行器记录，但当前无存活实例")));
        }
        if (input.getScheduleFailureCount() >= input.getScheduleFailThreshold()) {
            candidates.add(candidate(AlertRule.SCHEDULE_FAILURES, windowMinutes,
                    "调度失败次数过多",
                    Map.of(
                            "失败次数", String.valueOf(input.getScheduleFailureCount()),
                            "阈值", String.valueOf(input.getScheduleFailThreshold())
                    )));
        }
        return candidates;
    }

    public static String ruleLabel(AlertRule rule) {
        return switch (rule) {
            case LOW_SUCCESS_RATE -> "成功率过低";
            case HIGH_FAIL_COUNT -> "失败次数过多";
            case SLOW_P95 -> "P95 耗时过高";
            case NO_ONLINE_EXECUTOR -> "无在线执行器";
            case SCHEDULE_FAILURES -> "调度失败过多";
        };
    }

    private static SlaAlertCandidate candidate(AlertRule rule, int windowMinutes,
                                               String summary, Map<String, String> metrics) {
        return SlaAlertCandidate.builder()
                .rule(rule)
                .summary(summary)
                .metrics(metrics)
                .build();
    }
}
