package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_history")
public class AlertHistoryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String appCode;

    private String ruleCode;

    private String ruleLabel;

    private String summary;

    private String metricsJson;

    private Integer recipientCount;

    private String recipients;

    private Integer mailSent;

    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
