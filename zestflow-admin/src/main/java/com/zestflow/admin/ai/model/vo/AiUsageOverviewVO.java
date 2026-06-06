package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiUsageOverviewVO {

    private int days;
    private long totalSessions;
    private long successSessions;
    private double successRate;
    private long avgLatencyMs;
    private long totalTokenEstimate;
    private long adoptedCount;
    private long feedbackCount;
    private double adoptedRate;
    private Map<String, Long> sessionsByMode;
    private List<AiUsageDailyVO> dailyTrend;
}
