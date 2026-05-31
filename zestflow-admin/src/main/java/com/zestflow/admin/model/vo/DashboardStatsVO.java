package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO {

    private long totalModules;
    private long totalExecutors;
    private long healthyExecutors;
    private long errorExecutors;
    private long offlineExecutors;

    // 链 & 设计统计
    private long totalChains;
    private long enabledChains;
    private long totalDesigns;

    // 执行统计
    private long todayExecutions;
    private double avgExecutionMs;
    private double successRate;
}
