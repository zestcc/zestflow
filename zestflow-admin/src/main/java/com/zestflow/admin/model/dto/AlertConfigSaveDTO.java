package com.zestflow.admin.model.dto;

import lombok.Data;

/** SLA 告警配置保存 — 字段为 null 表示恢复为 yml 默认值 */
@Data
public class AlertConfigSaveDTO {

    private Boolean enabled;
    private Integer cooldownMinutes;
    private Integer windowMinutes;
    private Integer minExecutions;
    private Double successRateThreshold;
    private Integer failCountThreshold;
    private Long p95CostMsThreshold;
    private Integer scheduleFailThreshold;
    private Boolean alertNoOnlineExecutor;
    private String subjectPrefix;
}
