package com.zestflow.common.protocol;

import lombok.Builder;
import lombok.Data;

/** Collector SLA 扫描范围 + 有效配置（由 Admin internal API 返回） */
@Data
@Builder
public class SlaAlertScopeDTO {

    private Long tenantId;
    private String appCode;
    private boolean enabled;
    private int cooldownMinutes;
    private int windowMinutes;
    private int minExecutions;
    private double successRateThreshold;
    private int failCountThreshold;
    private long p95CostMsThreshold;
    private int scheduleFailThreshold;
    private boolean alertNoOnlineExecutor;
    private String subjectPrefix;
}
