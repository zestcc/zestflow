package com.zestflow.admin.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`tenant_ip_mapping`")
public class TenantIpMappingPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ipAddress;

    private Long tenantId;

    private LocalDateTime lastActiveAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
