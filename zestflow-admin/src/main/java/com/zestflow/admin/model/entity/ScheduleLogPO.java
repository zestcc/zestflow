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

    private String chainCode;

    private String executorId;

    private String executorAddress;

    private String routeStrategy;

    private String triggerType;

    private String params;

    private Integer status;

    private String resultData;

    private String errorMessage;

    private Long costMs;

    private LocalDateTime triggeredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
