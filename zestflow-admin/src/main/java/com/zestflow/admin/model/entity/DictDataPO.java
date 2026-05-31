package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_dict_data")
public class DictDataPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String typeCode;

    private String label;

    private String value;

    private Integer sort;

    private Integer status;

    private String tagType;

    private Integer defaultFlag;

    private String remark;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
