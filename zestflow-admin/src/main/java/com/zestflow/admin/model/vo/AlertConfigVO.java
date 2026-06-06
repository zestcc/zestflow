package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

/** SLA 告警配置 VO — effective 为当前生效值，defaults 为 yml 默认 */
@Data
@Builder
public class AlertConfigVO {

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

    /** 平台扫描间隔（毫秒），仅 yml 可配，修改需重启 */
    private long scanIntervalMs;

    private AlertConfigVO defaults;
    private boolean tenantOverride;
}
