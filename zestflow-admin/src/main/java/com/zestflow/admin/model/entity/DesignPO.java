package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("design")
public class DesignPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String designer;

    @TableField("module_id")
    private Long moduleId;

    private Integer status;

    private String description;

    @TableField("graph_data")
    private String graphData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
