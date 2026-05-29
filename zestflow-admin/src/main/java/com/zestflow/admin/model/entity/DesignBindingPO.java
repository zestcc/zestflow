package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("design_binding")
public class DesignBindingPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("design_id")
    private Long designId;

    @TableField("chain_id")
    private Long chainId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
