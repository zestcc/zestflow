package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_log")
public class ScheduleLogPO {

    @TableId(type = IdType.AUTO)
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

    private Long tenantId;

    private String appCode;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
