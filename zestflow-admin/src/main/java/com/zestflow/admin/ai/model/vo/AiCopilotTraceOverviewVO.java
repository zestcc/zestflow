package com.zestflow.admin.ai.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AiCopilotTraceOverviewVO {

    private int days;
    private long totalSteps;
    private long failedSteps;
    private double avgStepLatencyMs;
    private Map<String, Long> stepsByType;
    private List<AiCopilotTraceSessionRowVO> recentSessions;
}
