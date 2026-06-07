package com.zestflow.executor.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleLogPO {
    private Long id;
    private Long scheduleId;
    private String chainCode;
    private String executorId;
    private String executionId;
    private String routeStrategy;
    private String triggerType;
    private String params;
    private Integer status;
    private String errorMessage;
    private Long costMs;
    private String triggeredAt;
    private Long tenantId;
    private String appCode;
}
