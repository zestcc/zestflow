package com.zestflow.admin.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SLA 告警配置 — 收件人由 user_app_role 动态解析，不在此配置。
 */
@Data
@ConfigurationProperties(prefix = "zestflow.alert")
public class AlertProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** 扫描间隔（毫秒），与 StandaloneSlaAlertMonitor 的 @Scheduled 一致 */
    private long scanIntervalMs = 300_000L;

    /** 同一规则重复发信冷却（分钟） */
    private int cooldownMinutes = 30;

    /** 统计窗口（分钟） */
    private int windowMinutes = 60;

    /** 低于该执行次数时不评估成功率（避免样本过少误报） */
    private int minExecutions = 5;

    /** 成功率低于该值告警（0-100） */
    private double successRateThreshold = 95.0;

    /** 窗口内失败次数达到该值告警 */
    private int failCountThreshold = 10;

    /** P95 耗时超过该值（毫秒）告警 */
    private long p95CostMsThreshold = 5000L;

    /** 窗口内调度失败次数达到该值告警 */
    private int scheduleFailThreshold = 3;

    /** 有注册执行器但全部离线时告警 */
    private boolean alertNoOnlineExecutor = true;

    /** 邮件主题前缀 */
    private String subjectPrefix = "[ZestFlow 告警]";
}
