package com.zestflow.common.alert;

import com.zestflow.common.protocol.EventStats;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlaAlertEvaluationInput {

    private EventStats eventStats;
    private int windowMinutes;
    private int minExecutions;
    private double successRateThreshold;
    private int failCountThreshold;
    private long p95CostMsThreshold;
    private int scheduleFailThreshold;
    private boolean alertNoOnlineExecutor;
    private boolean hasRegisteredExecutors;
    private boolean hasOnlineExecutor;
    private long scheduleFailureCount;
}
