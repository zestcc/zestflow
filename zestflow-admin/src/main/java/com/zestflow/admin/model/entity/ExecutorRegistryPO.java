package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("executor_registry")
public class ExecutorRegistryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;

    private String executorId;

    private String appName;

    private String executorHost;

    private Integer executorPort;

    private Integer status;

    private LocalDateTime lastHeartbeat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
