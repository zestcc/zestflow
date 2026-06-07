package com.zestflow.common.alert;

/**
 * SLA 告警规则编码 — Admin / Collector 共用。
 */
public enum AlertRule {
    LOW_SUCCESS_RATE,
    HIGH_FAIL_COUNT,
    SLOW_P95,
    NO_ONLINE_EXECUTOR,
    SCHEDULE_FAILURES
}
