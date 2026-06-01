package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user_tenant`")
public class UserTenantPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long tenantId;

    /** 是否租户管理员：1-是 0-否 */
    private Integer isTenantAdmin;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
