package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfigPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configName;

    private String configValue;

    /** json / text / number / bool */
    private String valueType;

    private String category;

    private Integer status;

    private Integer sort;

    private String remark;

    private Long tenantId;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
