package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ScheduleVO {

    private Long id;
    private Long chainId;
    private String chainCode;
    private String chainName;
    private String jobType;
    private String jobKey;
    private String scheduleKind;
    private Long fixedIntervalMs;
    private String module;
    private Boolean editable;
    private Boolean remote;
    private LocalDateTime lastTriggerAt;
    private String cron;
    private String routeStrategy;
    private String params;
    private Integer status;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
