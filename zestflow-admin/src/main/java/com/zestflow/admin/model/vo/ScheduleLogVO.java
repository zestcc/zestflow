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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;
}
