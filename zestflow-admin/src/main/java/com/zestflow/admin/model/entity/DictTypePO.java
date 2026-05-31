package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_dict_type")
public class DictTypePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private Integer sort;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
