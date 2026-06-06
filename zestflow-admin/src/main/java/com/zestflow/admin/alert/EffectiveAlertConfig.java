package com.zestflow.admin.alert;

import lombok.Builder;
import lombok.Data;

/** 合并 yml 默认与租户 DB 覆盖后的有效告警配置 */
@Data
@Builder
public class EffectiveAlertConfig {

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
