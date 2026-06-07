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

    /** 同类型树形父级 ID */
    private Long parentId;

    /** 父级字典类型（空表示与 typeCode 相同） */
    private String parentTypeCode;

    /** 父级字典项 value，用于级联 */
    private String parentValue;

    private String label;

    private String value;

    private Integer sort;

    private Integer status;

    private String tagType;

    private Integer defaultFlag;

    private String remark;

    /** 扩展 JSON */
    private String extra;

    private Long tenantId;

    @TableField("app_code")
    private String appCode;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
