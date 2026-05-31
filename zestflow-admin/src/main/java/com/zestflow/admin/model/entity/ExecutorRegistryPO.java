package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("executor_registry")
public class ExecutorRegistryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String executorId;

    /** 应用编码（分组标识） */
    @TableField("app_code")
    private String appCode;

    /** 应用名称 */
    private String appName;

    private String executorHost;

    private Integer executorPort;

    private Integer status;

    private LocalDateTime lastHeartbeat;

    private Long tenantId;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
