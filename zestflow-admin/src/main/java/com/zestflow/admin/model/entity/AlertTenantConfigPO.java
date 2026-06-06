package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alert_tenant_config")
public class AlertTenantConfigPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Integer enabled;

    private Integer cooldownMinutes;

    private Integer windowMinutes;

    private Integer minExecutions;

    private BigDecimal successRateThreshold;

    private Integer failCountThreshold;

    private Long p95CostMsThreshold;

    private Integer scheduleFailThreshold;

    private Integer alertNoOnlineExecutor;

    private String subjectPrefix;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
