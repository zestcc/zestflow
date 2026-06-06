package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 调度日志统计 — 对标 xxl-job 调度报表摘要
 */
@Data
@Builder
public class ScheduleLogStatsVO {

    private long totalCount;
    private long successCount;
    private long failedCount;
    private long runningCount;
    private double successRate;
    private double avgCostMs;
}
