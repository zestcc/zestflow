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

    private String cron;

    private String routeStrategy;

    private String params;

    private Integer status;

    private String remark;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
