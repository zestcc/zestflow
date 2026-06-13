package com.zestflow.admin.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
