package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ScheduleLogVO {

    private Long id;
    private Long scheduleId;
    private String jobKey;
    private String jobName;
    private String chainCode;
    private String executorId;
    private String executorAddress;
    private String executionId;
    private String routeStrategy;
    private String triggerType;
    private String params;
    private Integer status;
    private String resultData;
    private String errorMessage;
    private Long costMs;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
}
