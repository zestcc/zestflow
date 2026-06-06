package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule")
public class SchedulePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chainId;

    private String chainCode;

    private String chainName;

    /** CHAIN | PLATFORM */
    private String jobType;

    /** 平台任务唯一键 */
    private String jobKey;

    private String cron;

    /** CRON | FIXED_RATE | FIXED_DELAY */
    private String scheduleKind;

    private Long fixedIntervalMs;

    private String module;

    /** 0-不可编辑 1-可编辑 */
    private Integer editable;

    /** 0-Admin执行 1-节点本地 */
    private Integer remote;

    private LocalDateTime lastTriggerAt;

    private String routeStrategy;

    private String params;

    private Integer status;

    private String remark;

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
