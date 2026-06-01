package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class UserPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String password;

    private String avatar;

    private Integer status;

    private Integer isSuperAdmin;

    private Integer mustChangePassword;

    private Long tenantId;

    private String createdBy;

    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    private Integer emailVerified;

    private String verifyToken;

    private LocalDateTime verifyTokenExpiry;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
