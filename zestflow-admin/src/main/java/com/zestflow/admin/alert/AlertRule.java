package com.zestflow.admin.alert;

/**
 * SLA 告警规则编码
 */
public enum AlertRule {
    LOW_SUCCESS_RATE,
    HIGH_FAIL_COUNT,
    SLOW_P95,
    NO_ONLINE_EXECUTOR,
    SCHEDULE_FAILURES
}
